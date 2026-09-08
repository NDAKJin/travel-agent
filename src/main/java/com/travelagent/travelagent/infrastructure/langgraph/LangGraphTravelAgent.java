package com.travelagent.travelagent.infrastructure.langgraph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationContextHolder;
import com.travelagent.travelagent.infrastructure.ai.prompt.PromptResourceLoader;
import com.travelagent.travelagent.infrastructure.planning.port.RoutePlanSemanticCache;
import com.travelagent.travelagent.infrastructure.planning.port.TravelWorkflowPort;
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
public class LangGraphTravelAgent implements TravelWorkflowPort {

    private static final int MAX_ROUTE_REVISIONS = 2;
    private static final String EMPTY_JSON = "{}";
    private static final String EMPTY_TEXT = "";
    private static final String ROUTE_INTENT = "route";
    private static final String NORMAL_INTENT = "normal";
    private static final String NODE_SUPERVISOR = "supervisor";
    private static final String NODE_REQUIREMENTS = "requirements";
    private static final String NODE_ROUTE_PLANNING = "routePlanning";
    private static final String NODE_ROUTE_PLANNER = "routePlanner";
    private static final String NODE_ROUTE_REVIEWER = "routeReviewer";
    private static final String NODE_NORMAL_SERVICE = "normalService";
    private static final String NODE_FINALIZE = "finalize";
    private static final String NODE_AWAIT_USER_INPUT = "awaitUserInput";
    private static final String STATE_HISTORY = "history";
    private static final String STATE_INTENT = "intent";
    private static final String STATE_CURRENT_LOCATION = "currentUserLocation";
    private static final String STATE_REQUIREMENTS = "requirements";
    private static final String STATE_ROUTE_PLAN = "routePlan";
    private static final String STATE_REVIEW = "review";
    private static final String STATE_REVIEW_APPROVED = "reviewApproved";
    private static final String STATE_REVIEW_ATTEMPTS = "reviewAttempts";
    private static final String STATE_ROUTE_NEXT = "routeNext";
    private static final String STATE_CACHE_HIT = "routeCacheHit";
    private static final String STATE_CACHE_SCORE = "routeCacheScore";
    private static final String STATE_NORMAL_REPLY = "normalReply";
    private static final String STATE_REPLY = "reply";
    private static final String QUESTIONS_PREFIX = "questions:";
    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.ofEntries(
            Map.entry(STATE_HISTORY, Channels.<List<AgentMessage>>base(() -> List.of())),
            Map.entry(STATE_INTENT, Channels.base(() -> EMPTY_TEXT)),
            Map.entry(STATE_CURRENT_LOCATION, Channels.base(() -> EMPTY_JSON)),
            Map.entry(STATE_REQUIREMENTS, Channels.base(() -> EMPTY_JSON)),
            Map.entry(STATE_ROUTE_PLAN, Channels.base(() -> EMPTY_TEXT)),
            Map.entry(STATE_REVIEW, Channels.base(() -> EMPTY_TEXT)),
            Map.entry(STATE_REVIEW_APPROVED, Channels.base(() -> false)),
            Map.entry(STATE_REVIEW_ATTEMPTS, Channels.base(() -> 0)),
            Map.entry(STATE_ROUTE_NEXT, Channels.base(() -> NODE_ROUTE_REVIEWER)),
            Map.entry(STATE_CACHE_HIT, Channels.base(() -> false)),
            Map.entry(STATE_CACHE_SCORE, Channels.base(() -> 0.0d)),
            Map.entry(STATE_NORMAL_REPLY, Channels.base(() -> EMPTY_TEXT)),
            Map.entry(STATE_REPLY, Channels.base(() -> EMPTY_TEXT)));

    private final ChatClient orchestrationChatClient;
    private final ChatClient routePlannerChatClient;
    private final ChatClient normalServiceChatClient;
    private final ChatClient finalizerChatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final BaseCheckpointSaver checkpointSaver;
    private final RoutePlanSemanticCache routePlanSemanticCache;
    private final CompileConfig compileConfig;
    private final StateGraph<WorkflowState> workflow;
    private final CompiledGraph<WorkflowState> graph;

    @org.springframework.beans.factory.annotation.Autowired
    public LangGraphTravelAgent(@Qualifier("orchestrationChatClient") ChatClient orchestrationChatClient,
                                @Qualifier("routePlannerChatClient") ChatClient routePlannerChatClient,
                                @Qualifier("normalServiceChatClient") ChatClient normalServiceChatClient,
                                @Qualifier("finalizerChatClient") ChatClient finalizerChatClient,
                                PromptResourceLoader promptResourceLoader,
                                BaseCheckpointSaver checkpointSaver,
                                RoutePlanSemanticCache routePlanSemanticCache) {
        this.orchestrationChatClient = orchestrationChatClient;
        this.routePlannerChatClient = routePlannerChatClient;
        this.normalServiceChatClient = normalServiceChatClient;
        this.finalizerChatClient = finalizerChatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.checkpointSaver = checkpointSaver;
        this.routePlanSemanticCache = routePlanSemanticCache;
        this.compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore(NODE_AWAIT_USER_INPUT)
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

    @Override
    public String run(List<AgentMessage> history, String conversationId,
                      AgentObservationContext observation, String currentUserLocation) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();
        Map<String, Object> input = new HashMap<>();
        input.put(STATE_HISTORY, history);
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
        }
    }

    public StateGraph<WorkflowState> studioWorkflow() {
        return workflow;
    }

    public CompileConfig studioCompileConfig() {
        return compileConfig;
    }

    @Override
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
                    .addNode(NODE_SUPERVISOR, AsyncNodeAction.node_async(this::supervise))
                    .addNode(NODE_REQUIREMENTS, AsyncNodeAction.node_async(this::collectRequirements))
                    .addSubgraph(NODE_ROUTE_PLANNING, buildRoutePlanningSubgraph())
                    .addNode(NODE_NORMAL_SERVICE, AsyncNodeAction.node_async(this::serveNormally))
                    .addNode(NODE_FINALIZE, AsyncNodeAction.node_async(this::finalizeReply))
                    .addNode(NODE_AWAIT_USER_INPUT, AsyncNodeAction.node_async(state -> Map.of()))
                    .addEdge(StateGraph.START, NODE_SUPERVISOR)
                    .addConditionalEdges(NODE_SUPERVISOR, AsyncEdgeAction.edge_async(this::afterSupervisor), edges(NODE_REQUIREMENTS, NODE_NORMAL_SERVICE))
                    .addConditionalEdges(NODE_REQUIREMENTS, AsyncEdgeAction.edge_async(this::afterRequirements), edges(NODE_ROUTE_PLANNING, NODE_FINALIZE))
                    .addEdge(NODE_ROUTE_PLANNING, NODE_FINALIZE)
                    .addEdge(NODE_NORMAL_SERVICE, NODE_FINALIZE)
                    .addConditionalEdges(NODE_FINALIZE, AsyncEdgeAction.edge_async(this::afterFinalize),
                            edges(NODE_AWAIT_USER_INPUT, StateGraph.END))
                    .addEdge(NODE_AWAIT_USER_INPUT, NODE_REQUIREMENTS);
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private StateGraph<WorkflowState> buildRoutePlanningSubgraph() throws GraphStateException {
        return new StateGraph<WorkflowState>(STATE_SCHEMA, WorkflowState::new)
                .addNode(NODE_ROUTE_PLANNER, AsyncNodeAction.node_async(this::planRoute))
                .addNode(NODE_ROUTE_REVIEWER, AsyncNodeAction.node_async(this::reviewRoute))
                .addEdge(StateGraph.START, NODE_ROUTE_PLANNER)
                .addConditionalEdges(NODE_ROUTE_PLANNER, AsyncEdgeAction.edge_async(this::afterRoutePlanner),
                        edges(NODE_ROUTE_REVIEWER))
                .addConditionalEdges(NODE_ROUTE_REVIEWER, AsyncEdgeAction.edge_async(this::afterReview),
                        edges(NODE_ROUTE_PLANNER, StateGraph.END));
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
                state.observation(), output -> ROUTE_INTENT.equals(WorkflowOutputParser.intent(output))
                        ? NODE_REQUIREMENTS : NODE_NORMAL_SERVICE);
        return Map.of(STATE_INTENT, ROUTE_INTENT.equals(WorkflowOutputParser.intent(decision)) ? ROUTE_INTENT : NORMAL_INTENT);
    }

    private Map<String, Object> collectRequirements(WorkflowState state) {
        String input = conversationJson(state);
        String systemPrompt = prompt("route-requirements");
        String raw = call(NODE_REQUIREMENTS, systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> WorkflowOutputParser.requirements(output).confirmed() ? NODE_ROUTE_PLANNER : NODE_FINALIZE);
        WorkflowOutputParser.RequirementDecision decision = WorkflowOutputParser.requirements(raw);
        return Map.of(STATE_REQUIREMENTS, decision.structuredOutput());
    }

    private Map<String, Object> planRoute(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            if (routePlanSemanticCache != null && state.initialRoutePlanning()) {
                String requirements = requirementData(state.requirements());
                var hit = routePlanSemanticCache.find(requirements);
                if (hit.isPresent()) {
                    return Map.of(STATE_ROUTE_PLAN, hit.get().routePlan(), STATE_ROUTE_NEXT, NODE_ROUTE_REVIEWER,
                            STATE_CACHE_HIT, true, STATE_CACHE_SCORE, hit.get().score());
                }
            }
            String systemPrompt = prompt("route-planner");
            String input = routePlanningInput(state);
            String raw = call(NODE_ROUTE_PLANNER, systemPrompt + "\n\n" + input,
                    routePlannerChatClient.prompt().system(systemPrompt).user(input).call(),
                    state.observation(), output -> WorkflowOutputParser.planner(output).next());
            WorkflowOutputParser.PlannerDecision decision = WorkflowOutputParser.planner(raw);
            return Map.of(STATE_ROUTE_PLAN, decision.plan(), STATE_ROUTE_NEXT, NODE_ROUTE_REVIEWER);
        }
    }

    private Map<String, Object> reviewRoute(WorkflowState state) {
        String input = routeReviewInput(state);
        String systemPrompt = prompt("route-reviewer");
        String raw = call(NODE_ROUTE_REVIEWER, systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> WorkflowOutputParser.review(output).approved()
                        || state.reviewAttempts() + 1 > MAX_ROUTE_REVISIONS
                        ? NODE_FINALIZE : NODE_ROUTE_PLANNER);
        WorkflowOutputParser.ReviewDecision decision = WorkflowOutputParser.review(raw);
        if (decision.approved() && routePlanSemanticCache != null && !state.routeCacheHit()
                && StringUtils.hasText(state.routePlan())) {
            routePlanSemanticCache.put(requirementData(state.requirements()), state.routePlan());
        }
        return Map.of(
                STATE_REVIEW, decision.structuredOutput(),
                STATE_REVIEW_APPROVED, decision.approved(),
                STATE_REVIEW_ATTEMPTS, state.reviewAttempts() + 1);
    }

    private Map<String, Object> serveNormally(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            String systemPrompt = prompt("normal-service");
            String input = normalServiceInput(state);
            String raw = call(NODE_NORMAL_SERVICE, systemPrompt + "\n\n" + input,
                    normalServiceChatClient.prompt().system(systemPrompt).user(input).call(),
                    state.observation(), output -> NODE_FINALIZE);
            return Map.of(STATE_NORMAL_REPLY, normalAnswer(raw));
        }
    }

    private Map<String, Object> finalizeReply(WorkflowState state) {
        if (ROUTE_INTENT.equals(state.intent()) && !state.requirementsConfirmed()) {
            String question = requirementQuestion(state.requirements());
            String systemPrompt = prompt("finalize");
            String input = finalRequirementInput(state.requirements());
            List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(input));
            String raw = call("finalize", systemPrompt + "\n\n" + input,
                    finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> NODE_AWAIT_USER_INPUT);
            return Map.of(STATE_REPLY, formatRequirementQuestion(finalReply(raw), question));
        }
        String result = finalResponseInput(state);
        List<Message> messages = List.of(new SystemMessage(prompt("finalize")), new UserMessage(result));
        String raw = call("finalize", prompt("finalize") + "\n\n" + result,
                finalizerChatClient.prompt().messages(messages).call(), state.observation(), output -> StateGraph.END);
        return Map.of(STATE_REPLY, finalReply(raw));
    }

    private String afterSupervisor(WorkflowState state) {
        return ROUTE_INTENT.equals(state.intent()) ? NODE_REQUIREMENTS : NODE_NORMAL_SERVICE;
    }

    private String afterRequirements(WorkflowState state) {
        return state.requirementsConfirmed() ? NODE_ROUTE_PLANNING : NODE_FINALIZE;
    }

    private String afterRoutePlanner(WorkflowState state) {
        return state.routeNext();
    }

    private String afterReview(WorkflowState state) {
        return !state.reviewApproved() && state.reviewAttempts() <= MAX_ROUTE_REVISIONS
                ? NODE_ROUTE_PLANNER : StateGraph.END;
    }

    private String afterFinalize(WorkflowState state) {
        return waitingForUser(state) ? NODE_AWAIT_USER_INPUT : StateGraph.END;
    }

    private boolean waitingForUser(WorkflowState state) {
        return ROUTE_INTENT.equals(state.intent()) && !state.requirementsConfirmed();
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

    private String stripPrefix(String value, String prefix) {
        if (!startsWith(value, prefix)) return value == null ? "" : value.trim();
        return value.trim().substring(prefix.length()).trim();
    }

    private String formatRequirementQuestion(String reply, String fallback) {
        String question = stripPrefix(reply, QUESTIONS_PREFIX);
        return StringUtils.hasText(question) ? question : fallback;
    }

    private String requirementData(String requirements) {
        JSONObject result = WorkflowOutputParser.parseJson(requirements);
        JSONObject data = result == null ? null : result.getJSONObject(STATE_REQUIREMENTS);
        return data == null ? "{}" : JSON.toJSONString(data, JSONWriter.Feature.WriteMapNullValue);
    }

    private String requirementQuestion(String requirements) {
        JSONObject result = WorkflowOutputParser.parseJson(requirements);
        return result == null ? EMPTY_TEXT : result.getString("question");
    }

    private String conversationJson(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("conversation", state.history());
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        return json(input);
    }

    private String routePlanningInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("phase", "ROUTE_PLANNING");
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        input.put("requirements", jsonOrText(requirementData(state.requirements())));
        input.put("review", state.review().isBlank() ? null : jsonOrText(state.review()));
        return json(input);
    }

    private String routeReviewInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("requirements", jsonOrText(requirementData(state.requirements())));
        input.put("routePlan", jsonOrText(state.routePlan()));
        return json(input);
    }

    private String finalRequirementInput(String requirements) {
        JSONObject input = new JSONObject();
        input.put("taskType", "REQUIREMENT_QUESTION");
        input.put("requirementDecision", jsonOrText(requirements));
        return json(input);
    }

    private String finalResponseInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("taskType", "FINAL_RESPONSE");
        if (ROUTE_INTENT.equals(state.intent())) {
            input.put("routePlan", jsonOrText(state.routePlan()));
            input.put("review", jsonOrText(state.review()));
        } else {
            input.put("normalService", state.normalReply());
        }
        return json(input);
    }

    private String normalServiceInput(WorkflowState state) {
        JSONObject input = new JSONObject();
        input.put("phase", "ANSWER");
        input.put("currentUserLocation", jsonOrText(state.currentUserLocation()));
        input.put("conversation", state.history());
        return json(input);
    }

    private Object jsonOrText(String value) {
        JSONObject json = WorkflowOutputParser.parseJson(value);
        return json == null ? value : json;
    }

    private String json(Object value) {
        return JSON.toJSONString(value, JSONWriter.Feature.WriteMapNullValue);
    }

    private String normalAnswer(String output) {
        JSONObject json = WorkflowOutputParser.parseJson(output);
        String answer = json == null ? null : json.getString("answer");
        return StringUtils.hasText(answer) ? answer.trim() : output == null ? "" : output.trim();
    }

    private String finalReply(String output) {
        JSONObject json = WorkflowOutputParser.parseJson(output);
        String reply = json == null ? null : json.getString("reply");
        return StringUtils.hasText(reply) ? reply.trim() : output == null ? "" : output.trim();
    }

    private String prompt(String name) {
        return promptResourceLoader.load(name);
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

        List<AgentMessage> history() { return this.<List<AgentMessage>>value(STATE_HISTORY).orElse(List.of()); }
        AgentObservationContext observation() {
            AgentObservationContext current = AgentObservationContextHolder.current();
            return current == null ? AgentObservationContext.disabled() : current;
        }
        String intent() { return this.<String>value(STATE_INTENT).orElse(EMPTY_TEXT); }
        String currentUserLocation() { return this.<String>value("currentUserLocation").orElse("{}"); }
        String requirements() { return this.<String>value(STATE_REQUIREMENTS).orElse(EMPTY_JSON); }
        boolean requirementsConfirmed() {
            JSONObject result = WorkflowOutputParser.parseJson(requirements());
            return result != null && "confirmed".equalsIgnoreCase(result.getString("status"));
        }
        String routePlan() { return this.<String>value(STATE_ROUTE_PLAN).orElse(EMPTY_TEXT); }
        String review() { return this.<String>value(STATE_REVIEW).orElse(EMPTY_TEXT); }
        boolean reviewApproved() { return this.<Boolean>value(STATE_REVIEW_APPROVED).orElse(false); }
        int reviewAttempts() { return this.<Integer>value(STATE_REVIEW_ATTEMPTS).orElse(0); }
        String routeNext() { return this.<String>value(STATE_ROUTE_NEXT).orElse(NODE_ROUTE_REVIEWER); }
        boolean routeCacheHit() { return this.<Boolean>value(STATE_CACHE_HIT).orElse(false); }
        boolean initialRoutePlanning() {
            return !StringUtils.hasText(routePlan()) && reviewAttempts() == 0 && !StringUtils.hasText(review());
        }
        String normalReply() { return this.<String>value(STATE_NORMAL_REPLY).orElse(EMPTY_TEXT); }
        String reply() { return this.<String>value(STATE_REPLY).orElse(EMPTY_TEXT); }
    }

}
