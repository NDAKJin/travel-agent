package com.travelagent.travelagent.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import com.travelagent.travelagent.infrastructure.config.AgentProperties;
import com.travelagent.travelagent.infrastructure.langgraph.LangGraphTravelAgent;
import com.travelagent.travelagent.infrastructure.ai.TokenCounter;
import org.springframework.ai.chat.client.ChatClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultReactAgentServiceTest {

        private static final AuthenticatedUser AUTHENTICATED_USER = new AuthenticatedUser(1L, "admin", "admin",
                        "ops-admin");

        @Mock
        private LangGraphTravelAgent agentGraph;

        @Mock
        private ChatClient summaryChatClient;

        private DefaultReactAgentService reactAgentService;

        @BeforeEach
        void setUp() {
                AgentProperties properties = new AgentProperties();
                properties.getProfile().setName("Travel Buddy");
                reactAgentService = new DefaultReactAgentService(
                                properties, agentGraph, new InMemoryAgentConversationStore(), event -> {
                                }, summaryChatClient, new TokenCounter(), "qwen-plus");
        }

        @Test
        void chatCreatesSessionWhenRequestDoesNotProvideOne() {
                when(agentGraph.run(eq(List.of(new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                "Plan a three-day Hangzhou trip"))), anyString(), any()))
                                .thenReturn("Welcome to Hangzhou");

                AgentChatResponse response = reactAgentService.chat(AUTHENTICATED_USER,
                                new AgentChatRequest("Plan a three-day Hangzhou trip", null));

                assertThat(response.sessionId()).isNotBlank();
                assertThat(response.reply()).isEqualTo("Welcome to Hangzhou");
        }

        @Test
        void hidesInternalQuestionsPrefixFromUser() {
                when(agentGraph.run(any(), anyString(), any())).thenReturn("questions: Please provide travel dates");

                AgentChatResponse response = reactAgentService.chat(AUTHENTICATED_USER,
                                new AgentChatRequest("I want to visit Hangzhou", "session-questions"));

                assertThat(response.reply()).isEqualTo("Please provide travel dates");
        }

        @Test
        void createSessionPersistsEmptyConversation() {
                AgentSessionSummaryResponse response = reactAgentService.createSession(AUTHENTICATED_USER);

                assertThat(response.sessionId()).isNotBlank();
                assertThat(response.title()).isEqualTo("new-chat");
                assertThat(response.messageCount()).isZero();
                assertThat(reactAgentService.listSessions(AUTHENTICATED_USER))
                                .extracting(AgentSessionSummaryResponse::sessionId)
                                .contains(response.sessionId());
        }

        @Test
        void chatReusesConversationHistoryForSameSession() {
                when(agentGraph.run(eq(List.of(new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                "What should I do on day one?"))), anyString(), any()))
                                .thenReturn("Start with West Lake");
                when(agentGraph.run(eq(List.of(
                                new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                                "What should I do on day one?"),
                                new com.travelagent.travelagent.domain.agent.model.AgentMessage("assistant",
                                                "Start with West Lake"),
                                new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                                "What about day two?"))),
                                anyString(), any()))
                                .thenReturn("Then visit Lingyin Temple");

                AgentChatResponse first = reactAgentService.chat(AUTHENTICATED_USER,
                                new AgentChatRequest("What should I do on day one?", "session-1"));
                AgentChatResponse second = reactAgentService.chat(AUTHENTICATED_USER,
                                new AgentChatRequest("What about day two?", "session-1"));

                assertThat(first.sessionId()).isEqualTo("session-1");
                assertThat(second.sessionId()).isEqualTo("session-1");
                assertThat(second.reply()).isEqualTo("Then visit Lingyin Temple");

        }

        @Test
        void listSessionsReturnsOnlyCurrentUsersSessions() {
                when(agentGraph.run(eq(List.of(new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                "Plan Hangzhou day one"))), anyString(), any()))
                                .thenReturn("Start with West Lake");
                when(agentGraph.run(eq(List.of(new com.travelagent.travelagent.domain.agent.model.AgentMessage("user",
                                "Plan Suzhou"))), anyString(), any()))
                                .thenReturn("Visit the tea fields");

                reactAgentService.chat(AUTHENTICATED_USER, new AgentChatRequest("Plan Hangzhou day one", "session-1"));
                reactAgentService.chat(new AuthenticatedUser(2L, "wx", "wx-open-id-demo", "wx-user"),
                                new AgentChatRequest("Plan Suzhou", "session-2"));

                assertThat(reactAgentService.listSessions(AUTHENTICATED_USER))
                                .extracting(session -> session.sessionId())
                                .containsExactly("session-1");
        }
}
