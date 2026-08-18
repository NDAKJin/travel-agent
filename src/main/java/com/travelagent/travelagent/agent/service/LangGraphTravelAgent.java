package com.travelagent.travelagent.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.observation.AgentObservationContextHolder;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.HashMap;

@Service
public class LangGraphTravelAgent {

    // ponytail: review loop is capped at two revisions; add persisted human approval when longer workflows are needed.
    private static final int MAX_ROUTE_REVISIONS = 2;
    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.ofEntries(
            Map.entry("history", Channels.<List<AgentMessage>>base(() -> List.of())),
            Map.entry("intent", Channels.base(() -> "")),
            Map.entry("currentUserLocation", Channels.base(() -> "{}")),
            Map.entry("requirements", Channels.base(() -> "{}")),
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
    private final BaseCheckpointSaver checkpointSaver;
    private final CompileConfig compileConfig;
    private final StateGraph<WorkflowState> workflow;
    private final CompiledGraph<WorkflowState> graph;

    public LangGraphTravelAgent(@Qualifier("orchestrationChatClient") ChatClient orchestrationChatClient,
                                @Qualifier("routePlannerChatClient") ChatClient routePlannerChatClient,
                                @Qualifier("normalServiceChatClient") ChatClient normalServiceChatClient,
                                @Qualifier("finalizerChatClient") ChatClient finalizerChatClient,
                                PromptResourceLoader promptResourceLoader) {
        this(orchestrationChatClient, routePlannerChatClient, normalServiceChatClient, finalizerChatClient,
                promptResourceLoader, new MemorySaver());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LangGraphTravelAgent(@Qualifier("orchestrationChatClient") ChatClient orchestrationChatClient,
                                @Qualifier("routePlannerChatClient") ChatClient routePlannerChatClient,
                                @Qualifier("normalServiceChatClient") ChatClient normalServiceChatClient,
                                @Qualifier("finalizerChatClient") ChatClient finalizerChatClient,
                                PromptResourceLoader promptResourceLoader,
                                BaseCheckpointSaver checkpointSaver) {
        this.orchestrationChatClient = orchestrationChatClient;
        this.routePlannerChatClient = routePlannerChatClient;
        this.normalServiceChatClient = normalServiceChatClient;
        this.finalizerChatClient = finalizerChatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.checkpointSaver = checkpointSaver;
        this.compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore("awaitUserInput")
                .build();
        this.workflow = buildWorkflow();
        this.graph = compile(workflow);
    }

    public String run(List<AgentMessage> history) {
        return run(history, AgentObservationContext.disabled());
    }

    public String run(List<AgentMessage> history, AgentObservationContext observation) {
        return run(history, observation.traceId(), observation);
    }

    public String run(List<AgentMessage> history, String conversationId, AgentObservationContext observation) {
        return run(history, conversationId, observation, null);
    }

    public String run(List<AgentMessage> history, String conversationId,
                      AgentObservationContext observation, String currentUserLocation) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .putMetadata(AgentObservationContext.METADATA_KEY, observation)
                .build();
        Map<String, Object> input = new HashMap<>();
        input.put("history", history);
        if (StringUtils.hasText(currentUserLocation)) {
            input.put("currentUserLocation", currentUserLocation);
        }
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(observation)) {
            boolean resumesRequirementCollection = graph.stateOf(config).isPresent();
            WorkflowState state = (resumesRequirementCollection
                    ? graph.invoke(GraphInput.resume(input), config)
                    : graph.invoke(input, config))
                    .orElseThrow(() -> new IllegalStateException("Travel agent graph produced no response"));
            releaseCompletedThread(config, state);
            return state.reply();
        } finally {
            observation.close();
        }
    }

    public StateGraph<WorkflowState> studioWorkflow() {
        return workflow;
    }

    public CompileConfig studioCompileConfig() {
        return compileConfig;
    }

    public void clear(String conversationId) {
        try {
            checkpointSaver.release(RunnableConfig.builder().threadId(conversationId).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to clear graph checkpoint", exception);
        }
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
                    .addNode("awaitUserInput", AsyncNodeAction.node_async(state -> Map.of()))
                    .addEdge(StateGraph.START, "supervisor")
                    .addConditionalEdges("supervisor", AsyncEdgeAction.edge_async(this::afterSupervisor), edges("requirements", "normalService"))
                    .addConditionalEdges("requirements", AsyncEdgeAction.edge_async(this::afterRequirements), edges("routePlanner", "finalize"))
                    .addEdge("routePlanner", "routeReviewer")
                    .addConditionalEdges("routeReviewer", AsyncEdgeAction.edge_async(this::afterReview), edges("routePlanner", "finalize"))
                    .addEdge("normalService", "finalize")
                    .addConditionalEdges("finalize", AsyncEdgeAction.edge_async(this::afterFinalize),
                            edges("awaitUserInput", StateGraph.END))
                    .addEdge("awaitUserInput", "requirements");
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private CompiledGraph<WorkflowState> compile(StateGraph<WorkflowState> workflow) {
        try {
            return workflow.compile(compileConfig);
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private Map<String, Object> supervise(WorkflowState state) {
        String input = conversationJson(state);
        String systemPrompt = prompt("intent-supervisor");
        String decision = call("supervisor", systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> "route".equals(parseIntent(output))
                        ? "requirements" : "normalService");
        return Map.of("intent", "route".equals(parseIntent(decision)) ? "route" : "normal");
    }

    private Map<String, Object> collectRequirements(WorkflowState state) {
        String input = conversationJson(state);
        String systemPrompt = prompt("route-requirements");
        String raw = call("requirements", systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> parseRequirements(output).confirmed() ? "routePlanner" : "finalize");
        RequirementDecision decision = parseRequirements(raw);
        return Map.of("requirements", decision.structuredOutput());
    }

    private Map<String, Object> planRoute(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            String systemPrompt = prompt("route-planner");
            String input = routePlanningInput(state);
            String plan = call("routePlanner", systemPrompt + "\n\n" + input,
                    routePlannerChatClient.prompt().system(systemPrompt).user(input).call(),
                    state.observation(), output -> "routeReviewer");
            return Map.of("routePlan", plan);
        }
    }

    private Map<String, Object> reviewRoute(WorkflowState state) {
        String input = routeReviewInput(state);
        String systemPrompt = prompt("route-reviewer");
        String raw = call("routeReviewer", systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> parseReview(output).approved() || state.reviewAttempts() + 1 > MAX_ROUTE_REVISIONS
                        ? "finalize" : "routePlanner");
        ReviewDecision decision = parseReview(raw);
        return Map.of(
                "review", decision.structuredOutput(),
                "reviewApproved", decision.approved(),
                "reviewAttempts", state.reviewAttempts() + 1);
    }

    private Map<String, Object> serveNormally(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            String systemPrompt = prompt("normal-service");
            String input = normalServiceInput(state);
            String raw = call("normalService", systemPrompt + "\n\n" + input,
                    normalServiceChatClient.prompt().system(systemPrompt).user(input).call(),
                    state.observation(), output -> "finalize");
            return Map.of("normalReply", normalAnswer(raw));
        }
    }

    private Map<String, Object> finalizeReply(WorkflowState state) {
        if ("route".equals(state.intent()) && !state.requirementsConfirmed()) {
            String question = requirementQuestion(state.requirements());
            String systemPrompt = prompt("finalize");
            String input = finalRequirementInput(state.requirements());
            List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(input));
            String raw = call("finalize", systemPrompt + "\n\n" + input,
                    finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> "awaitUserInput");
            return Map.of("reply", formatRequirementQuestion(finalReply(raw), question));
        }
        String result = finalResponseInput(state);
        List<Message> messages = List.of(new SystemMessage(prompt("finalize")), new UserMessage(result));
        String raw = call("finalize", prompt("finalize") + "\n\n" + result,
                finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> StateGraph.END);
        return Map.of("reply", finalReply(raw));
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

    private String afterFinalize(WorkflowState state) {
        return waitingForUser(state) ? "awaitUserInput" : StateGraph.END;
    }

    private boolean waitingForUser(WorkflowState state) {
        return "route".equals(state.intent()) && !state.requirementsConfirmed();
    }

    private void releaseCompletedThread(RunnableConfig config, WorkflowState state) {
        if (waitingForUser(state)) return;
        try {
            checkpointSaver.release(config);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to release completed graph state", exception);
        }
    }

    private Map<String, String> edges(String... nodes) {
        return java.util.Arrays.stream(nodes).collect(java.util.stream.Collectors.toMap(node -> node, node -> node));
    }

    private boolean startsWith(String value, String prefix) {
        return value != null && value.trim().toLowerCase().startsWith(prefix);
    }

    private String parseIntent(String output) {
        JSONObject json = parseJson(output);
        String intent = json != null && json.size() == 1 && json.containsKey("intent") ? json.getString("intent") : null;
        return "route".equalsIgnoreCase(intent) ? "route" : "normal";
    }

    private RequirementDecision parseRequirements(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validRequirements(json)) {
            return new RequirementDecision("confirmed".equalsIgnoreCase(json.getString("status")),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put("status", "QUESTION");
        fallback.put("question", "请先补充本次路线规划的必要信息。");
        fallback.put("requirements", emptyRequirements());
        return new RequirementDecision(false, JSON.toJSONString(fallback, JSONWriter.Feature.WriteMapNullValue));
    }

    private ReviewDecision parseReview(String output) {
        JSONObject json = parseJson(output);
        if (json != null && validReview(json)) {
            return new ReviewDecision("approved".equalsIgnoreCase(json.getString("status")),
                    JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue));
        }
        JSONObject fallback = new JSONObject();
        fallback.put("status", "REVISE");
        fallback.put("issues", List.of("审核结果格式无效，请重新审核。"));
        return new ReviewDecision(false, JSON.toJSONString(fallback));
    }

    private boolean validRequirements(JSONObject value) {
        String status = value.getString("status");
        JSONObject requirements = value.getJSONObject("requirements");
        if (value.size() != 3 || !value.containsKey("status") || !value.containsKey("question")
                || requirements == null || !hasRequirementFields(requirements)) return false;
        if ("question".equalsIgnoreCase(status)) return StringUtils.hasText(value.getString("question"));
        return "confirmed".equalsIgnoreCase(status) && StringUtils.hasText(requirements.getString("origin"))
                && StringUtils.hasText(requirements.getString("destination"))
                && StringUtils.hasText(requirements.getString("people"))
                && StringUtils.hasText(requirements.getString("budget"))
                && (StringUtils.hasText(requirements.getString("date")) || StringUtils.hasText(requirements.getString("days")));
    }

    private boolean hasRequirementFields(JSONObject requirements) {
        return requirements.size() == 8 && List.of("origin", "destination", "date", "days", "people", "budget", "interests", "constraints")
                .stream().allMatch(requirements::containsKey);
    }

    private JSONObject emptyRequirements() {
        JSONObject requirements = new JSONObject();
        List.of("origin", "destination", "date", "days", "people", "budget", "interests", "constraints")
                .forEach(field -> requirements.put(field, null));
        return requirements;
    }

    private boolean validReview(JSONObject value) {
        String status = value.getString("status");
        JSONArray issues = value.getJSONArray("issues");
        if (value.size() != 2 || !value.containsKey("status") || !value.containsKey("issues") || issues == null) return false;
        if ("approved".equalsIgnoreCase(status)) return issues.isEmpty();
        return "revise".equalsIgnoreCase(status) && !issues.isEmpty() && issues.size() <= 3
                && issues.stream().allMatch(item -> item instanceof String text && StringUtils.hasText(text));
    }

    private static JSONObject parseJson(String output) {
        if (!StringUtils.hasText(output)) return null;
        String value = output.trim();
        if (!value.startsWith("{") || !value.endsWith("}")) return null;
        try {
            return JSON.parseObject(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stripPrefix(String value, String prefix) {
        if (!startsWith(value, prefix)) return value == null ? "" : value.trim();
        return value.trim().substring(prefix.length()).trim();
    }

    private String formatRequirementQuestion(String reply, String fallback) {
        String question = stripPrefix(reply, "questions:");
        return StringUtils.hasText(question) ? question : fallback;
    }

    private String requirementData(String requirements) {
        JSONObject result = parseJson(requirements);
        JSONObject data = result == null ? null : result.getJSONObject("requirements");
        return data == null ? "{}" : JSON.toJSONString(data, JSONWriter.Feature.WriteMapNullValue);
    }

    private String requirementQuestion(String requirements) {
        JSONObject result = parseJson(requirements);
        return result == null ? "" : result.getString("question");
    }

    private String conversationJson(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("conversation", state.history());
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private String routePlanningInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("phase", "ROUTE_PLANNING");
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        input.put("requirements", jsonOrText(requirementData(state.requirements())));
        input.put("review", state.review().isBlank() ? null : jsonOrText(state.review()));
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private String routeReviewInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("requirements", jsonOrText(requirementData(state.requirements())));
        input.put("routePlan", jsonOrText(state.routePlan()));
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private String finalRequirementInput(String requirements) {
        JSONObject input = new JSONObject();
        input.put("taskType", "REQUIREMENT_QUESTION");
        input.put("requirementDecision", jsonOrText(requirements));
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private String finalResponseInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("taskType", "FINAL_RESPONSE");
        if ("route".equals(state.intent())) {
            input.put("routePlan", jsonOrText(state.routePlan()));
            input.put("review", jsonOrText(state.review()));
        } else {
            input.put("normalService", state.normalReply());
        }
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private String normalServiceInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("phase", "ANSWER");
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        input.put("conversation", state.history());
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private Object jsonOrText(String value) {
        JSONObject json = parseJson(value);
        return json == null ? value : json;
    }

    private String normalAnswer(String output) {
        JSONObject json = parseJson(output);
        String answer = json == null ? null : json.getString("answer");
        return StringUtils.hasText(answer) ? answer.trim() : output == null ? "" : output.trim();
    }

    private String finalReply(String output) {
        JSONObject json = parseJson(output);
        String reply = json == null ? null : json.getString("reply");
        return StringUtils.hasText(reply) ? reply.trim() : output == null ? "" : output.trim();
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

    public static class WorkflowState extends AgentState {

        WorkflowState(Map<String, Object> data) {
            super(data);
        }

        List<AgentMessage> history() { return this.<List<AgentMessage>>value("history").orElse(List.of()); }
        AgentObservationContext observation() {
            AgentObservationContext current = AgentObservationContextHolder.current();
            return current == null ? AgentObservationContext.disabled() : current;
        }
        String intent() { return this.<String>value("intent").orElse(""); }
        String currentUserLocation() { return this.<String>value("currentUserLocation").orElse("{}"); }
        String requirements() { return this.<String>value("requirements").orElse("{}"); }
        boolean requirementsConfirmed() {
            JSONObject result = parseJson(requirements());
            return result != null && "confirmed".equalsIgnoreCase(result.getString("status"));
        }
        String routePlan() { return this.<String>value("routePlan").orElse(""); }
        String review() { return this.<String>value("review").orElse(""); }
        boolean reviewApproved() { return this.<Boolean>value("reviewApproved").orElse(false); }
        int reviewAttempts() { return this.<Integer>value("reviewAttempts").orElse(0); }
        String normalReply() { return this.<String>value("normalReply").orElse(""); }
        String reply() { return this.<String>value("reply").orElse(""); }
    }

    private record RequirementDecision(boolean confirmed, String structuredOutput) { }

    private record ReviewDecision(boolean approved, String structuredOutput) { }
}
