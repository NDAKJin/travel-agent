package com.travelagent.travelagent.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.observation.AgentObservationContextHolder;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class LangGraphTravelAgent {

    // ponytail: review loop is capped at two revisions; add persisted human approval when longer workflows are needed.
    private static final int MAX_ROUTE_REVISIONS = 2;
    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.ofEntries(
            Map.entry("history", Channels.<List<AgentMessage>>base(() -> List.of())),
            Map.entry("observation", Channels.base(AgentObservationContext::disabled)),
            Map.entry("intent", Channels.base(() -> "")),
            Map.entry("requirementsConfirmed", Channels.base(() -> false)),
            Map.entry("requirementsReply", Channels.base(() -> "")),
            Map.entry("requirementsData", Channels.base(() -> "{}")),
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
    private final StateGraph<WorkflowState> workflow;
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
        this.workflow = buildWorkflow();
        this.graph = compile(workflow);
    }

    public String run(List<AgentMessage> history) {
        return run(history, AgentObservationContext.disabled());
    }

    public String run(List<AgentMessage> history, AgentObservationContext observation) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(observation.traceId())
                .putMetadata(AgentObservationContext.METADATA_KEY, observation)
                .build();
        try {
            return graph.invoke(Map.of("history", history, "observation", observation), config)
                    .map(WorkflowState::reply)
                    .orElseThrow(() -> new IllegalStateException("Travel agent graph produced no response"));
        } finally {
            observation.close();
        }
    }

    public StateGraph<WorkflowState> studioWorkflow() {
        return workflow;
    }

    private StateGraph<WorkflowState> buildWorkflow() {
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
                    .addEdge("finalize", StateGraph.END);
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private CompiledGraph<WorkflowState> compile(StateGraph<WorkflowState> workflow) {
        try {
            return workflow.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private Map<String, Object> supervise(WorkflowState state) {
        if (awaitingRequirements(state.history())) {
            return Map.of("intent", "route");
        }
        String input = conversation(state.history());
        String decision = call("supervisor", input,
                orchestrationChatClient.prompt().system(prompt("intent-supervisor")).user(input).call(),
                state.observation(), output -> "route".equals(parseIntent(output))
                        ? "requirements" : "normalService");
        return Map.of("intent", "route".equals(parseIntent(decision)) ? "route" : "normal");
    }

    private Map<String, Object> collectRequirements(WorkflowState state) {
        String input = conversation(state.history());
        String raw = call("requirements", input,
                orchestrationChatClient.prompt().system(prompt("route-requirements")).user(input).call(),
                state.observation(), output -> parseRequirements(output).confirmed() ? "routePlanner" : "finalize");
        RequirementDecision decision = parseRequirements(raw);
        return Map.of(
                "requirementsConfirmed", decision.confirmed(),
                "requirementsReply", decision.protocolReply(),
                "requirementsData", decision.requirements());
    }

    private Map<String, Object> planRoute(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            String systemPrompt = prompt("route-planner") + "\n已确认需求（结构化）：\n" + state.requirementsData()
                    + (state.review().isBlank() ? "" : "\n审核修改要求：\n" + state.review());
            String plan = call("routePlanner", systemPrompt + "\n\n" + conversation(state.history()),
                    routePlannerChatClient.prompt().messages(toMessages(systemPrompt, state.history())).call(),
                    state.observation(), output -> "routeReviewer");
            return Map.of("routePlan", plan);
        }
    }

    private Map<String, Object> reviewRoute(WorkflowState state) {
        String input = "已确认需求（结构化）：\n" + state.requirementsData() + "\n\n行程方案：\n" + state.routePlan();
        String raw = call("routeReviewer", input,
                orchestrationChatClient.prompt().system(prompt("route-reviewer")).user(input).call(),
                state.observation(), output -> parseReview(output).approved() || state.reviewAttempts() + 1 > MAX_ROUTE_REVISIONS
                        ? "finalize" : "routePlanner");
        ReviewDecision decision = parseReview(raw);
        return Map.of(
                "review", decision.issues(),
                "reviewApproved", decision.approved(),
                "reviewAttempts", state.reviewAttempts() + 1);
    }

    private Map<String, Object> serveNormally(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            String systemPrompt = prompt("normal-service");
            String reply = call("normalService", systemPrompt + "\n\n" + conversation(state.history()),
                    normalServiceChatClient.prompt().messages(toMessages(systemPrompt, state.history())).call(),
                    state.observation(), output -> "finalize");
            return Map.of("normalReply", reply);
        }
    }

    private Map<String, Object> finalizeReply(WorkflowState state) {
        if ("route".equals(state.intent()) && !state.requirementsConfirmed()) {
            String question = stripPrefix(state.requirementsReply(), "questions:");
            String systemPrompt = prompt("finalize")
                    + "\n\n当前路线需求尚未确认，只能润色并返回待确认问题。"
                    + "不得生成路线、行程、景点推荐或任何最终方案。";
            List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(question));
            String reply = call("finalize", systemPrompt + "\n\n待确认问题：\n" + question,
                    finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> "end");
            return Map.of("reply", "questions: " + (StringUtils.hasText(reply) ? reply.trim() : question));
        }
        String result = "route".equals(state.intent())
                ? "路线规划方案：\n" + state.routePlan() + "\n\n审核意见：\n" + state.review()
                : "普通服务结果：\n" + state.normalReply();
        List<Message> messages = toMessages(prompt("finalize"), state.history());
        messages.add(new UserMessage(result));
        return Map.of("reply", call("finalize", prompt("finalize") + "\n\n" + result,
                finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> "end"));
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

    private String parseIntent(String output) {
        JSONObject json = parseJson(output);
        if (json != null && StringUtils.hasText(json.getString("intent"))) {
            return json.getString("intent").trim().toLowerCase();
        }
        return output == null ? "" : output.trim().toLowerCase();
    }

    private RequirementDecision parseRequirements(String output) {
        JSONObject json = parseJson(output);
        if (json != null) {
            boolean confirmed = "confirmed".equalsIgnoreCase(json.getString("status"));
            JSONObject requirements = json.getJSONObject("requirements");
            String data = requirements == null ? "{}" : requirements.toJSONString();
            String question = json.getString("question");
            if (confirmed) return new RequirementDecision(true, "confirmed: " + data, data);
            if (StringUtils.hasText(question)) return new RequirementDecision(false, "questions: " + question.trim(), data);
        }
        boolean confirmed = startsWith(output, "confirmed:");
        return new RequirementDecision(confirmed, output == null ? "" : output.trim(), "{}" );
    }

    private ReviewDecision parseReview(String output) {
        JSONObject json = parseJson(output);
        if (json != null) {
            boolean approved = "approved".equalsIgnoreCase(json.getString("status"));
            String issues = json.getString("issues");
            if (issues == null && json.getJSONArray("issues") != null) {
                issues = json.getJSONArray("issues").toJSONString();
            }
            return new ReviewDecision(approved, issues == null ? "" : issues);
        }
        return new ReviewDecision(startsWith(output, "approve:"), output == null ? "" : output.trim());
    }

    private JSONObject parseJson(String output) {
        if (!StringUtils.hasText(output)) return null;
        String value = output.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSON.parseObject(value.substring(start, end + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stripPrefix(String value, String prefix) {
        if (!startsWith(value, prefix)) return value == null ? "" : value.trim();
        return value.trim().substring(prefix.length()).trim();
    }

    private boolean awaitingRequirements(List<AgentMessage> history) {
        return history.size() > 1
                && "assistant".equalsIgnoreCase(history.get(history.size() - 2).role())
                && startsWith(history.get(history.size() - 2).content(), "questions:");
    }

    private String prompt(String name) {
        return promptResourceLoader.load(name);
    }

    private AgentObservationContext observation(RunnableConfig config) {
        Object value = config.metadata(AgentObservationContext.METADATA_KEY).orElse(null);
        return value instanceof AgentObservationContext context ? context : AgentObservationContext.disabled();
    }

    private String call(String agent, String input, ChatClient.CallResponseSpec request,
                        AgentObservationContext observation,
                        java.util.function.Function<String, String> nextDecision) {
        Instant startedAt = Instant.now();
        try {
            ChatResponse response = request.chatResponse();
            String output = response.getResult().getOutput().getText();
            observation.publish(agent, "llm", "success", startedAt, input, output, response,
                    nextDecision.apply(output), null);
            return output;
        } catch (RuntimeException exception) {
            observation.publish(agent, "llm", "error", startedAt, input, null, null, null, exception);
            throw exception;
        }
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

    public static class WorkflowState extends AgentState {

        WorkflowState(Map<String, Object> data) {
            super(data);
        }

        List<AgentMessage> history() { return this.<List<AgentMessage>>value("history").orElse(List.of()); }
        AgentObservationContext observation() { return this.<AgentObservationContext>value("observation").orElseGet(AgentObservationContext::disabled); }
        String intent() { return this.<String>value("intent").orElse(""); }
        boolean requirementsConfirmed() { return this.<Boolean>value("requirementsConfirmed").orElse(false); }
        String requirementsReply() { return this.<String>value("requirementsReply").orElse(""); }
        String requirementsData() { return this.<String>value("requirementsData").orElse("{}"); }
        String routePlan() { return this.<String>value("routePlan").orElse(""); }
        String review() { return this.<String>value("review").orElse(""); }
        boolean reviewApproved() { return this.<Boolean>value("reviewApproved").orElse(false); }
        int reviewAttempts() { return this.<Integer>value("reviewAttempts").orElse(0); }
        String normalReply() { return this.<String>value("normalReply").orElse(""); }
        String reply() { return this.<String>value("reply").orElse(""); }
    }

    private record RequirementDecision(boolean confirmed, String protocolReply, String requirements) { }

    private record ReviewDecision(boolean approved, String issues) { }
}
