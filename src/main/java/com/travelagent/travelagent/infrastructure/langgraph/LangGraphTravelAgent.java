package com.travelagent.travelagent.infrastructure.langgraph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationContextHolder;
import com.travelagent.travelagent.infrastructure.ai.prompt.PromptResourceLoader;
import com.travelagent.travelagent.infrastructure.ai.agent.BudgetAgent;
import com.travelagent.travelagent.infrastructure.ai.agent.KnowledgePlanningAgent;
import com.travelagent.travelagent.infrastructure.ai.agent.RoutePlanningAgent;
import com.travelagent.travelagent.infrastructure.planning.port.RouteExpertGateway;
import com.travelagent.travelagent.infrastructure.planning.port.RoutePlanSemanticCache;
import com.travelagent.travelagent.infrastructure.planning.port.TravelWorkflowPort;
import com.travelagent.travelagent.infrastructure.planning.agent.SpringAiRouteExpertGateway;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.HashMap;

@Service
public class LangGraphTravelAgent implements TravelWorkflowPort {

    private static final Set<String> ROUTE_EXPERTS = Set.of("KNOWLEDGE", "ROUTE", "BUDGET");
    private static final int MAX_ROUTE_REVISIONS = 2;
    private static final int DEFAULT_ROUTE_EXPERT_TIMEOUT_SECONDS = 20;
    private static final Map<String, Channel<?>> STATE_SCHEMA = Map.ofEntries(
            Map.entry("history", Channels.<List<AgentMessage>>base(() -> List.of())),
            Map.entry("intent", Channels.base(() -> "")),
            Map.entry("currentUserLocation", Channels.base(() -> "{}")),
            Map.entry("requirements", Channels.base(() -> "{}")),
            Map.entry("routePlan", Channels.base(() -> "")),
            Map.entry("review", Channels.base(() -> "")),
            Map.entry("reviewApproved", Channels.base(() -> false)),
            Map.entry("reviewAttempts", Channels.base(() -> 0)),
            Map.entry("routeNext", Channels.base(() -> "routeReviewer")),
            Map.entry("expertTasks", Channels.base(() -> "[]")),
            Map.entry("expertResults", Channels.base(() -> "{}")),
            Map.entry("routeCacheHit", Channels.base(() -> false)),
            Map.entry("routeCacheScore", Channels.base(() -> 0.0d)),
            Map.entry("normalReply", Channels.base(() -> "")),
            Map.entry("reply", Channels.base(() -> "")));

    private final ChatClient orchestrationChatClient;
    private final ChatClient routePlannerChatClient;
    private final ChatClient normalServiceChatClient;
    private final ChatClient finalizerChatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final BaseCheckpointSaver checkpointSaver;
    private final RouteExpertGateway routeExpertGateway;
    private final RoutePlanSemanticCache routePlanSemanticCache;
    private final Executor routeExpertExecutor;
    @Value("${travel-agent.route-expert.timeout-seconds:20}")
    private int routeExpertTimeoutSeconds = DEFAULT_ROUTE_EXPERT_TIMEOUT_SECONDS;
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
                                KnowledgePlanningAgent knowledgeAgent,
                                RoutePlanningAgent routeAgent,
                                BudgetAgent budgetAgent,
                                RoutePlanSemanticCache routePlanSemanticCache,
                                @Qualifier("routeExpertExecutor") Executor routeExpertExecutor) {
        this.orchestrationChatClient = orchestrationChatClient;
        this.routePlannerChatClient = routePlannerChatClient;
        this.normalServiceChatClient = normalServiceChatClient;
        this.finalizerChatClient = finalizerChatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.checkpointSaver = checkpointSaver;
        this.routeExpertGateway = new SpringAiRouteExpertGateway(knowledgeAgent, routeAgent, budgetAgent);
        this.routePlanSemanticCache = routePlanSemanticCache;
        this.routeExpertExecutor = routeExpertExecutor;
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

    @Override
    public String run(List<AgentMessage> history, String conversationId,
                      AgentObservationContext observation, String currentUserLocation) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
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
                    .addNode("supervisor", AsyncNodeAction.node_async(this::supervise))
                    .addNode("requirements", AsyncNodeAction.node_async(this::collectRequirements))
                    .addSubgraph("routePlanning", buildRoutePlanningSubgraph())
                    .addNode("normalService", AsyncNodeAction.node_async(this::serveNormally))
                    .addNode("finalize", AsyncNodeAction.node_async(this::finalizeReply))
                    .addNode("awaitUserInput", AsyncNodeAction.node_async(state -> Map.of()))
                    .addEdge(StateGraph.START, "supervisor")
                    .addConditionalEdges("supervisor", AsyncEdgeAction.edge_async(this::afterSupervisor), edges("requirements", "normalService"))
                    .addConditionalEdges("requirements", AsyncEdgeAction.edge_async(this::afterRequirements), edges("routePlanning", "finalize"))
                    .addEdge("routePlanning", "finalize")
                    .addEdge("normalService", "finalize")
                    .addConditionalEdges("finalize", AsyncEdgeAction.edge_async(this::afterFinalize),
                            edges("awaitUserInput", StateGraph.END))
                    .addEdge("awaitUserInput", "requirements");
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Unable to build travel agent graph", exception);
        }
    }

    private StateGraph<WorkflowState> buildRoutePlanningSubgraph() throws GraphStateException {
        return new StateGraph<WorkflowState>(STATE_SCHEMA, WorkflowState::new)
                .addNode("routePlanner", AsyncNodeAction.node_async(this::planRoute))
                .addNode("expertsParallel", AsyncNodeAction.node_async(this::runExpertsParallel))
                .addNode("routeReviewer", AsyncNodeAction.node_async(this::reviewRoute))
                .addEdge(StateGraph.START, "routePlanner")
                .addConditionalEdges("routePlanner", AsyncEdgeAction.edge_async(this::afterRoutePlanner),
                        edges("expertsParallel", "routeReviewer"))
                .addEdge("expertsParallel", "routePlanner")
                .addConditionalEdges("routeReviewer", AsyncEdgeAction.edge_async(this::afterReview),
                        edges("routePlanner", StateGraph.END));
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
                state.observation(), output -> "route".equals(WorkflowOutputParser.intent(output))
                        ? "requirements" : "normalService");
        return Map.of("intent", "route".equals(WorkflowOutputParser.intent(decision)) ? "route" : "normal");
    }

    private Map<String, Object> collectRequirements(WorkflowState state) {
        String input = conversationJson(state);
        String systemPrompt = prompt("route-requirements");
        String raw = call("requirements", systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> WorkflowOutputParser.requirements(output).confirmed() ? "routePlanner" : "finalize");
        WorkflowOutputParser.RequirementDecision decision = WorkflowOutputParser.requirements(raw);
        return Map.of("requirements", decision.structuredOutput());
    }

    private Map<String, Object> planRoute(WorkflowState state) {
        try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
            if (routePlanSemanticCache != null && state.initialRoutePlanning()) {
                String requirements = requirementData(state.requirements());
                var hit = routePlanSemanticCache.find(requirements);
                if (hit.isPresent()) {
                    return Map.of("routePlan", hit.get().routePlan(), "routeNext", "routeReviewer",
                            "routeCacheHit", true, "routeCacheScore", hit.get().score());
                }
            }
            String systemPrompt = prompt("route-planner");
            String input = routePlanningInput(state);
            String raw = call("routePlanner", systemPrompt + "\n\n" + input,
                    routePlannerChatClient.prompt().system(systemPrompt).user(input).call(),
                    state.observation(), output -> WorkflowOutputParser.planner(output).next());
            WorkflowOutputParser.PlannerDecision decision = WorkflowOutputParser.planner(raw);
            if (!decision.delegates()) {
                return Map.of("routePlan", decision.plan(), "routeNext", "routeReviewer");
            }
            return Map.of("routeNext", decision.next(), "expertTasks", decision.tasks());
        }
    }

    private Map<String, Object> runExpertsParallel(WorkflowState state) {
        JSONObject parsedResults = WorkflowOutputParser.parseJson(state.expertResults());
        final JSONObject results = parsedResults == null ? new JSONObject() : parsedResults;
        JSONArray tasks = JSON.parseArray(state.expertTasks());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Set<String> scheduled = new HashSet<>();
        for (Object item : tasks == null ? List.of() : tasks) {
            JSONObject task = item instanceof JSONObject json ? json : null;
            if (task == null) continue;
            String expert = task.getString("expert");
            if (!StringUtils.hasText(expert)) continue;
            expert = expert.trim().toUpperCase(Locale.ROOT);
            if (!ROUTE_EXPERTS.contains(expert)) continue;
            if (task.get("task") == null) continue;
            String payload = JSON.toJSONString(task.get("task"), JSONWriter.Feature.WriteMapNullValue);
            // 每轮每个专家只保留一个任务，避免结果按专家键覆盖并浪费并行调用。
            if (!scheduled.add(expert)) continue;
            String resultKey = expert.toLowerCase(Locale.ROOT);
            if (results.containsKey(resultKey)) continue;
            String selectedExpert = expert;
            futures.add(CompletableFuture.runAsync(() -> {
                try (AgentObservationContextHolder.Scope ignored = AgentObservationContextHolder.open(state.observation())) {
                    try {
                        String output = routeExpertGateway.execute(selectedExpert, payload);
                        synchronized (results) {
                            results.put(resultKey, jsonOrText(output));
                        }
                    } catch (RuntimeException exception) {
                        synchronized (results) {
                            results.put(resultKey, Map.of("error", "专家暂时不可用"));
                        }
                    }
                }
            }, routeExpertExecutor));
        }
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .orTimeout(Math.max(1, routeExpertTimeoutSeconds), TimeUnit.SECONDS)
                        .join();
            } catch (CompletionException exception) {
                // A slow external expert must not block route planning indefinitely.
                synchronized (results) {
                    for (String expert : scheduled) {
                        String resultKey = expert.toLowerCase(Locale.ROOT);
                        results.putIfAbsent(resultKey, Map.of("error", "专家响应超时"));
                    }
                }
            }
        }
        return Map.of("expertResults", JSON.toJSONString(results, JSONWriter.Feature.WriteMapNullValue));
    }

    private Map<String, Object> reviewRoute(WorkflowState state) {
        String input = routeReviewInput(state);
        String systemPrompt = prompt("route-reviewer");
        String raw = call("routeReviewer", systemPrompt + "\n\n" + input,
                orchestrationChatClient.prompt().system(systemPrompt).user(input).call(),
                state.observation(), output -> WorkflowOutputParser.review(output).approved()
                        || state.reviewAttempts() + 1 > MAX_ROUTE_REVISIONS
                        ? "finalize" : "routePlanner");
        WorkflowOutputParser.ReviewDecision decision = WorkflowOutputParser.review(raw);
        if (decision.approved() && routePlanSemanticCache != null && !state.routeCacheHit()
                && StringUtils.hasText(state.routePlan())) {
            routePlanSemanticCache.put(requirementData(state.requirements()), state.routePlan());
        }
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
        return state.requirementsConfirmed() ? "routePlanning" : "finalize";
    }

    private String afterRoutePlanner(WorkflowState state) {
        return state.routeNext();
    }

    private String afterReview(WorkflowState state) {
        return !state.reviewApproved() && state.reviewAttempts() <= MAX_ROUTE_REVISIONS
                ? "routePlanner" : StateGraph.END;
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

    private String stripPrefix(String value, String prefix) {
        if (!startsWith(value, prefix)) return value == null ? "" : value.trim();
        return value.trim().substring(prefix.length()).trim();
    }

    private String formatRequirementQuestion(String reply, String fallback) {
        String question = stripPrefix(reply, "questions:");
        return StringUtils.hasText(question) ? question : fallback;
    }

    private String requirementData(String requirements) {
        JSONObject result = WorkflowOutputParser.parseJson(requirements);
        JSONObject data = result == null ? null : result.getJSONObject("requirements");
        return data == null ? "{}" : JSON.toJSONString(data, JSONWriter.Feature.WriteMapNullValue);
    }

    private String requirementQuestion(String requirements) {
        JSONObject result = WorkflowOutputParser.parseJson(requirements);
        return result == null ? "" : result.getString("question");
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
        input.put("expertResults", jsonOrText(state.expertResults()));
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
        if ("route".equals(state.intent())) {
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

        List<AgentMessage> history() { return this.<List<AgentMessage>>value("history").orElse(List.of()); }
        AgentObservationContext observation() {
            AgentObservationContext current = AgentObservationContextHolder.current();
            return current == null ? AgentObservationContext.disabled() : current;
        }
        String intent() { return this.<String>value("intent").orElse(""); }
        String currentUserLocation() { return this.<String>value("currentUserLocation").orElse("{}"); }
        String requirements() { return this.<String>value("requirements").orElse("{}"); }
        boolean requirementsConfirmed() {
            JSONObject result = WorkflowOutputParser.parseJson(requirements());
            return result != null && "confirmed".equalsIgnoreCase(result.getString("status"));
        }
        String routePlan() { return this.<String>value("routePlan").orElse(""); }
        String review() { return this.<String>value("review").orElse(""); }
        boolean reviewApproved() { return this.<Boolean>value("reviewApproved").orElse(false); }
        int reviewAttempts() { return this.<Integer>value("reviewAttempts").orElse(0); }
        String routeNext() { return this.<String>value("routeNext").orElse("routeReviewer"); }
        String expertTasks() { return this.<String>value("expertTasks").orElse("[]"); }
        String expertResults() { return this.<String>value("expertResults").orElse("{}"); }
        boolean routeCacheHit() { return this.<Boolean>value("routeCacheHit").orElse(false); }
        boolean initialRoutePlanning() {
            return !StringUtils.hasText(routePlan()) && "[]".equals(expertTasks()) && "{}".equals(expertResults());
        }
        String normalReply() { return this.<String>value("normalReply").orElse(""); }
        String reply() { return this.<String>value("reply").orElse(""); }
    }

}
