package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.prompt.PromptProvider;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import com.travelagent.travelagent.config.AgentProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

@ExtendWith(MockitoExtension.class)
class DefaultReactAgentServiceTest {

    private static final AuthenticatedUser AUTHENTICATED_USER =
            new AuthenticatedUser(1L, "admin", "admin", "ops-admin");

    @Mock
    private PromptProvider promptProvider;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private DefaultReactAgentService reactAgentService;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.getProfile().setName("Travel Buddy");
        properties.getTool().setEnabled(true);
        properties.getQwen().setModel("qwen-plus");
        reactAgentService = new DefaultReactAgentService(
                properties, promptProvider, chatClient, new InMemoryAgentConversationStore());
    }

    @Test
    void chatCreatesSessionWhenRequestDoesNotProvideOne() {
        when(promptProvider.systemPrompt()).thenReturn("system prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.messages(any(List.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Welcome to Hangzhou")))));

        AgentChatResponse response = reactAgentService.chat(AUTHENTICATED_USER, new AgentChatRequest("Plan a three-day Hangzhou trip", null));

        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.reply()).isEqualTo("Welcome to Hangzhou");
        assertThat(response.toolEnabled()).isTrue();
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
        when(promptProvider.systemPrompt()).thenReturn("system prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.messages(any(List.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse())
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Start with West Lake")))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Then visit Lingyin Temple")))));

        AgentChatResponse first = reactAgentService.chat(AUTHENTICATED_USER, new AgentChatRequest("What should I do on day one?", "session-1"));
        AgentChatResponse second = reactAgentService.chat(AUTHENTICATED_USER, new AgentChatRequest("What about day two?", "session-1"));

        assertThat(first.sessionId()).isEqualTo("session-1");
        assertThat(second.sessionId()).isEqualTo("session-1");
        assertThat(second.reply()).isEqualTo("Then visit Lingyin Temple");

        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatClientRequestSpec, times(2)).messages(messagesCaptor.capture());
        List<Message> messages = messagesCaptor.getAllValues().getLast();
        assertThat(messages).extracting(Message::getText).containsExactly(
                "system prompt",
                "What should I do on day one?",
                "Start with West Lake",
                "What about day two?");
    }

    @Test
    void listSessionsReturnsOnlyCurrentUsersSessions() {
        when(promptProvider.systemPrompt()).thenReturn("system prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.messages(any(List.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse())
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Start with West Lake")))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Visit the tea fields")))));

        reactAgentService.chat(AUTHENTICATED_USER, new AgentChatRequest("Plan Hangzhou day one", "session-1"));
        reactAgentService.chat(new AuthenticatedUser(2L, "wx", "wx-open-id-demo", "wx-user"), new AgentChatRequest("Plan Suzhou", "session-2"));

        assertThat(reactAgentService.listSessions(AUTHENTICATED_USER))
                .extracting(session -> session.sessionId())
                .containsExactly("session-1");
    }
}
