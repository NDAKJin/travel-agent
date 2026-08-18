package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.subagent.BudgetAgent;
import com.travelagent.travelagent.agent.subagent.KnowledgePlanningAgent;
import com.travelagent.travelagent.agent.subagent.RoutePlanningAgent;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class LangGraphTravelAgentTest {

    @Test
    void asksForMissingRouteRequirementsWithoutPlanning() {
        ChatClient planner = client();
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), question("Where would you like to go?")), planner, client(),
                client(finalReply("Where would you like to go?")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a trip"))))
                .isEqualTo("Where would you like to go?");
        verify(planner, org.mockito.Mockito.never()).prompt();
    }

    @Test
    void approvedRouteIsPlannedReviewedAndFinalized() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), confirmed(), approved()), client(plan()), client(),
                client(finalReply("Final itinerary")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a one-day trip"))))
                .isEqualTo("Final itinerary");
    }

    @Test
    void invalidControlResponsesDoNotAdvanceRoutePlanning() {
        ChatClient planner = client();
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), "{\"status\":\"CONFIRMED\",\"requirements\":{}}"), planner, client(),
                client(finalReply("Please provide the required details.")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a trip"))))
                .isEqualTo("Please provide the required details.");
        verify(planner, org.mockito.Mockito.never()).prompt();
    }

    @Test
    void invalidIntentFallsBackToNormalService() {
        ChatClient planner = client();
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client("not-json"), planner, client(normalAnswer("Nearby places")),
                client(finalReply("Nearby places")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "What is nearby?"))))
                .isEqualTo("Nearby places");
        verify(planner, org.mockito.Mockito.never()).prompt();
    }

    @Test
    void requirementFollowUpResumesAtRequirementsNode() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), question("Where do you start?"), confirmed(), approved()), client(plan()), client(),
                client(finalReply("Where do you start?"), finalReply("Final itinerary")), new PromptResourceLoader());
        String question = graph.run(List.of(new AgentMessage("user", "Plan a day trip")),
                "1:session-1", AgentObservationContext.disabled());

        assertThat(question).isEqualTo("Where do you start?");
        assertThat(graph.run(List.of(
                new AgentMessage("user", "Plan a day trip"),
                new AgentMessage("assistant", question),
                new AgentMessage("user", "Start at the station")),
                "1:session-1", AgentObservationContext.disabled())).isEqualTo("Final itinerary");
    }

    @Test
    void rejectedRouteReturnsToPlannerBeforeFinalizing() {
        ChatClient planner = client(plan(), plan());
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), confirmed(), revise(), approved()), planner, client(),
                client(finalReply("Final itinerary")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a one-day trip"))))
                .isEqualTo("Final itinerary");
        verify(planner, org.mockito.Mockito.times(2)).prompt();
    }

    @Test
    void delegatesExpertInsideRoutePlanningSubgraph() {
        KnowledgePlanningAgent knowledge = mock(KnowledgePlanningAgent.class);
        when(knowledge.planKnowledge(anyString())).thenReturn("{\"facts\":[]}");
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(route(), confirmed(), approved()),
                client(delegate("KNOWLEDGE"), plan()), client(), client(finalReply("Final itinerary")),
                new PromptResourceLoader(), new MemorySaver(), knowledge,
                mock(RoutePlanningAgent.class), mock(BudgetAgent.class));

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a one-day trip"))))
                .isEqualTo("Final itinerary");
        verify(knowledge).planKnowledge(anyString());
    }

    @Test
    void normalRequestUsesNormalServiceThenFinalizer() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client(normal()), client(), client(normalAnswer("Service answer")),
                client(finalReply("Final answer")), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "What is nearby?"))))
                .isEqualTo("Final answer");
    }

    private static String route() {
        return "{\"intent\":\"route\"}";
    }

    private static String normal() {
        return "{\"intent\":\"normal\"}";
    }

    private static String question(String question) {
        return "{\"status\":\"QUESTION\",\"question\":\"" + question
                + "\",\"requirements\":{\"origin\":null,\"destination\":null,\"date\":null,"
                + "\"days\":null,\"people\":null,\"budget\":null,\"interests\":null,\"constraints\":null}}";
    }

    private static String confirmed() {
        return "{\"status\":\"CONFIRMED\",\"question\":null,\"requirements\":{"
                + "\"origin\":\"Nanjing Station\",\"destination\":\"Nanjing South Station\","
                + "\"date\":null,\"days\":\"1\",\"people\":\"2\",\"budget\":\"1000\","
                + "\"interests\":null,\"constraints\":null}}";
    }

    private static String plan() {
        return "{\"itinerary\":[],\"budget\":{\"knownItems\":[],\"unknownItems\":[],\"summary\":\"\"},"
                + "\"notes\":[],\"pending\":[]}";
    }

    private static String delegate(String expert) {
        return "{\"action\":\"DELEGATE\",\"expert\":\"" + expert
                + "\",\"task\":{\"requirements\":{}}}";
    }

    private static String approved() {
        return "{\"status\":\"APPROVED\",\"issues\":[]}";
    }

    private static String revise() {
        return "{\"status\":\"REVISE\",\"issues\":[\"Add transport details\"]}";
    }

    private static String normalAnswer(String answer) {
        return "{\"answer\":\"" + answer + "\"}";
    }

    private static String finalReply(String reply) {
        return "{\"reply\":\"" + reply + "\"}";
    }

    private ChatClient client(String... responses) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        Queue<String> values = new ArrayDeque<>(List.of(responses));
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.messages(any(List.class))).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.chatResponse()).thenAnswer(ignored -> new ChatResponse(
                List.of(new Generation(new AssistantMessage(values.remove())))));
        return client;
    }
}
