package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LangGraphTravelAgent {

    // ponytail: review loop is capped at two revisions; add persisted human approval when longer workflows are needed.
    private static final int MAX_ROUTE_REVISIONS = 2;
    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.ofEntries(
            Map.entry("history", Channels.<List<AgentMessage>>base(() -> List.of())),
            Map.entry("intent", Channels.base(() -> "")),
            Map.entry("requirementsConfirmed", Channels.base(() -> false)),
            Map.entry("requirementsReply", Channels.base(() -> "")),
            Map.entry("routePlan", Channels.base(() -> "")),
            Map.entry("review", Channels.base(() -> "")),
            Map.entry("reviewApproved", Channels.base(() -> false)),
            Map.entry("reviewAttempts", Channels.base(() -> 0)),
            Map.entry("normalReply", Channels.base(() -> "")),
            Map.entry("reply", Channels.base(() -> "")));

    private final ChatClient orchestrationChatClient;
    private final ChatClient routePlannerChatClient;
    private final ChatClient normalServiceChatClient;
    private final ChatClient finalizerChatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final CompiledGraph<WorkflowState> graph;

    public LangGraphTravelAgent(@Qualifier("orchestrationChatClient") ChatClient orchestrationChatClient,
                                @Qualifier("routePlannerChatClient") ChatClient routePlannerChatClient,
                                @Qualifier("normalServiceChatClient") ChatClient normalServiceChatClient,
                                @Qualifier("finalizerChatClient") ChatClient finalizerChatClient,
                                PromptResourceLoader promptResourceLoader) {
        this.orchestrationChatClient = orchestrationChatClient;
        this.routePlannerChatClient = routePlannerChatClient;
        this.normalServiceChatClient = normalServiceChatClient;
        this.finalizerChatClient = finalizerChatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.graph = buildGraph();
    }

    public String run(List<AgentMessage> history) {
        return graph.invoke(Map.of("history", history))
                .map(WorkflowState::reply)
                .orElseThrow(() -> new IllegalStateException("Travel agent graph produced no response"));
    }

    private CompiledGraph<WorkflowState> buildGraph() {
        try {
            return new StateGraph<WorkflowState>(STATE_SCHEMA, WorkflowState::new)
                    .addNode("supervisor", AsyncNodeAction.node_async(this::supervise))
                    .addNode("requirements", AsyncNodeAction.node_async(this::collectRequirements))
                    .addNode("routePlanner", AsyncNodeAction.node_async(this::planRoute))
                    .addNode("routeReviewer", AsyncNodeAction.node_async(this::reviewRoute))
                    .addNode("normalService", AsyncNodeAction.node_async(this::serveNormally))
                    .addNode("finalize", AsyncNodeAction.node_async(this::finalizeReply))
                    .addEdge(StateGraph.START, "supervisor")
                    .addConditionalEdges("supervisor", AsyncEdgeAction.edge_async(this::afterSupervisor), edges("requirements", "normalService"))
                    .addConditionalEdges("requirements", AsyncEdgeAction.edge_async(this::afterRequirements), edges("routePlanner", "finalize"))
                    .addEdge("routePlanner", "routeReviewer")
                    .addConditionalEdges("routeReviewer", AsyncEdgeAction.edge_async(this::afterReview), edges("routePlanner", "finalize"))
                    .addEdge("normalService", "finalize")
                    .addEdge("finalize", StateGraph.END)
                    .compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private Map<String, Object> supervise(WorkflowState state) {
        if (awaitingRequirements(state.history())) {
            return Map.of("intent", "route");
        }
        String decision = orchestrationChatClient.prompt().system(prompt("intent-supervisor")).user(conversation(state.history())).call().content();
        return Map.of("intent", "route".equalsIgnoreCase(decision == null ? "" : decision.trim()) ? "route" : "normal");
    }

    private Map<String, Object> collectRequirements(WorkflowState state) {
        String reply = orchestrationChatClient.prompt().system(prompt("route-requirements")).user(conversation(state.history())).call().content();
        return Map.of("requirementsConfirmed", startsWith(reply, "confirmed:"), "requirementsReply", reply);
    }

    private Map<String, Object> planRoute(WorkflowState state) {
        String plan = routePlannerChatClient.prompt()
                .messages(toMessages(prompt("route-planner") + "\n" + state.requirementsReply()
                        + (state.review().isBlank() ? "" : "\n审核修改要求：\n" + state.review()), state.history()))
                .call()
                .content();
        return Map.of("routePlan", plan);
    }

    private Map<String, Object> reviewRoute(WorkflowState state) {
        String review = orchestrationChatClient.prompt().system(prompt("route-reviewer"))
                .user("已确认需求：\n" + state.requirementsReply() + "\n\n行程方案：\n" + state.routePlan())
                .call()
                .content();
        return Map.of(
                "review", review,
                "reviewApproved", startsWith(review, "approve:"),
                "reviewAttempts", state.reviewAttempts() + 1);
    }

    private Map<String, Object> serveNormally(WorkflowState state) {
        String reply = normalServiceChatClient.prompt()
                .messages(toMessages(prompt("normal-service"), state.history()))
                .call()
                .content();
        return Map.of("normalReply", reply);
    }

    private Map<String, Object> finalizeReply(WorkflowState state) {
        if ("route".equals(state.intent()) && !state.requirementsConfirmed()) {
            return Map.of("reply", state.requirementsReply());
        }
        String result = "route".equals(state.intent())
                ? "路线规划方案：\n" + state.routePlan() + "\n\n审核意见：\n" + state.review()
                : "普通服务结果：\n" + state.normalReply();
        List<Message> messages = toMessages(prompt("finalize"), state.history());
        messages.add(new UserMessage(result));
        return Map.of("reply", finalizerChatClient.prompt().messages(messages).call().content());
    }

    private String afterSupervisor(WorkflowState state) {
        return "route".equals(state.intent()) ? "requirements" : "normalService";
    }

    private String afterRequirements(WorkflowState state) {
        return state.requirementsConfirmed() ? "routePlanner" : "finalize";
    }

    private String afterReview(WorkflowState state) {
        return !state.reviewApproved() && state.reviewAttempts() <= MAX_ROUTE_REVISIONS ? "routePlanner" : "finalize";
    }

    private Map<String, String> edges(String... nodes) {
        return java.util.Arrays.stream(nodes).collect(java.util.stream.Collectors.toMap(node -> node, node -> node));
    }

    private boolean startsWith(String value, String prefix) {
        return value != null && value.trim().toLowerCase().startsWith(prefix);
    }

    private boolean awaitingRequirements(List<AgentMessage> history) {
        return history.size() > 1
                && "assistant".equalsIgnoreCase(history.get(history.size() - 2).role())
                && startsWith(history.get(history.size() - 2).content(), "questions:");
    }

    private String prompt(String name) {
        return promptResourceLoader.load(name);
    }

    private String conversation(List<AgentMessage> history) {
        return history.stream().map(message -> message.role() + ": " + message.content()).collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<Message> toMessages(String systemPrompt, List<AgentMessage> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (AgentMessage message : history) {
            messages.add(toMessage(message));
        }
        return messages;
    }

    private Message toMessage(AgentMessage message) {
        if ("assistant".equalsIgnoreCase(message.role())) {
            return new AssistantMessage(message.content());
        }
        return StringUtils.hasText(message.role()) && !"user".equalsIgnoreCase(message.role())
                ? new SystemMessage(message.content()) : new UserMessage(message.content());
    }

    private static class WorkflowState extends AgentState {

        WorkflowState(Map<String, Object> data) {
            super(data);
        }

        List<AgentMessage> history() { return this.<List<AgentMessage>>value("history").orElse(List.of()); }
        String intent() { return this.<String>value("intent").orElse(""); }
        boolean requirementsConfirmed() { return this.<Boolean>value("requirementsConfirmed").orElse(false); }
        String requirementsReply() { return this.<String>value("requirementsReply").orElse(""); }
        String routePlan() { return this.<String>value("routePlan").orElse(""); }
        String review() { return this.<String>value("review").orElse(""); }
        boolean reviewApproved() { return this.<Boolean>value("reviewApproved").orElse(false); }
        int reviewAttempts() { return this.<Integer>value("reviewAttempts").orElse(0); }
        String normalReply() { return this.<String>value("normalReply").orElse(""); }
        String reply() { return this.<String>value("reply").orElse(""); }
    }
}
