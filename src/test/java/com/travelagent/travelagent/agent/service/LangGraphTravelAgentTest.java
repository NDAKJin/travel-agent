package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class LangGraphTravelAgentTest {

    @Test
    void asksForMissingRouteRequirementsWithoutPlanning() {
        ChatClient orchestration = client("route", "questions: Which city and how many days?");
        ChatClient planner = client();
        LangGraphTravelAgent graph = new LangGraphTravelAgent(orchestration, planner, client(), client(), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan a trip"))))
                .isEqualTo("questions: Which city and how many days?");
        verify(planner, org.mockito.Mockito.never()).prompt();
    }

    @Test
    void approvedRouteIsPlannedReviewedAndFinalized() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client("route", "confirmed: Hangzhou, two days", "approve: feasible"),
                client("draft itinerary"), client(), client("final itinerary"), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan two days in Hangzhou"))))
                .isEqualTo("final itinerary");
    }

    @Test
    void followUpToRequirementsSkipsIntentClassification() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client("confirmed: Hangzhou, two days", "approve: feasible"),
                client("draft itinerary"), client(), client("final itinerary"), new PromptResourceLoader());

        assertThat(graph.run(List.of(
                new AgentMessage("user", "Plan a trip"),
                new AgentMessage("assistant", "questions: Which city and how many days?"),
                new AgentMessage("user", "Hangzhou, two days"))))
                .isEqualTo("final itinerary");
    }

    @Test
    void rejectedRouteReturnsToPlannerBeforeFinalizing() {
        ChatClient planner = client("draft itinerary", "revised itinerary");
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client("route", "confirmed: Hangzhou, two days", "revise: add transport", "approve: feasible"),
                planner, client(), client("final itinerary"), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "Plan two days in Hangzhou"))))
                .isEqualTo("final itinerary");
        verify(planner, org.mockito.Mockito.times(2)).prompt();
    }

    @Test
    void normalRequestUsesNormalServiceThenFinalizer() {
        LangGraphTravelAgent graph = new LangGraphTravelAgent(
                client("normal"), client(), client("service answer"), client("final answer"), new PromptResourceLoader());

        assertThat(graph.run(List.of(new AgentMessage("user", "What is nearby?"))))
                .isEqualTo("final answer");
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
