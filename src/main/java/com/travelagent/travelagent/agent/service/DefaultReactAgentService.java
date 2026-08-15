package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.prompt.TravelAssistantPromptProvider;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import com.travelagent.travelagent.config.AgentProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class DefaultReactAgentService {

    private final AgentProperties agentProperties;
    private final TravelAssistantPromptProvider promptProvider;
    private final ChatClient chatClient;
    private final AgentConversationStore conversationStore;
    private final String model;

    public DefaultReactAgentService(AgentProperties agentProperties,
                                    TravelAssistantPromptProvider promptProvider,
                                    ChatClient chatClient,
                                    AgentConversationStore conversationStore,
                                    @Value("${SPRING_AI_DASHSCOPE_CHAT_OPTIONS_MODEL:qwen3.7-flash}") String model) {
        this.agentProperties = agentProperties;
        this.promptProvider = promptProvider;
        this.chatClient = chatClient;
        this.conversationStore = conversationStore;
        this.model = model;
    }

    public AgentChatResponse chat(AuthenticatedUser user, AgentChatRequest request) {
        if (request.message().length() > agentProperties.getMaxMessageChars()) {
            throw new IllegalArgumentException("message exceeds the configured maximum length");
        }
        if (request.sessionId() != null && request.sessionId().length() > agentProperties.getMaxSessionIdChars()) {
            throw new IllegalArgumentException("sessionId exceeds the configured maximum length");
        }
        String sessionId = normalizeSessionId(request.sessionId());
        CurrentUserLocationContext.set(request.location());
        LocationPermissionContext.clear();
        log.info("Starting react-agent chat: sessionId={}, requestedSessionId={}, toolEnabled={}",
                sessionId,
                request.sessionId(),
                agentProperties.getTool().isEnabled());
        try {
            AgentSessionContext existingSession = conversationStore.load(user.userId(), sessionId).orElse(null);
            List<AgentMessage> history = new ArrayList<>(boundedHistory(
                    existingSession == null ? List.of() : existingSession.messages()));
            log.debug("Loaded conversation history: sessionId={}, existingMessageCount={}", sessionId, history.size());
            history.add(new AgentMessage("user", request.message()));

            String reply = callModel(history);
            boolean locationPermissionRequired = LocationPermissionContext.isRequested();

            if (!locationPermissionRequired) {
                history.add(new AgentMessage("assistant", reply));
                Instant now = Instant.now();
                Instant createdAt = existingSession == null ? now : existingSession.createdAt();
                List<AgentMessage> storedHistory = boundedHistory(history);
                conversationStore.append(user.userId(),
                        new AgentSessionContext(sessionId, storedHistory, createdAt, now),
                        List.of(new AgentMessage("user", request.message()), new AgentMessage("assistant", reply)));
            }
            log.info("Completed react-agent chat: sessionId={}, totalMessageCount={}, replyLength={}",
                    sessionId,
                    history.size(),
                    reply.length());
            return new AgentChatResponse(
                    sessionId,
                    reply,
                    agentProperties.getProfile().getName(),
                    model,
                    agentProperties.getTool().isEnabled(),
                    NearbySearchContext.get(),
                    locationPermissionRequired);
        }
        finally {
            NearbySearchContext.clear();
            LocationPermissionContext.clear();
            CurrentUserLocationContext.clear();
        }
    }

    public AgentSessionSummaryResponse createSession(AuthenticatedUser user) {
        Instant now = Instant.now();
        String sessionId = UUID.randomUUID().toString();
        conversationStore.save(user.userId(), new AgentSessionContext(sessionId, List.of(), now, now));
        return new AgentSessionSummaryResponse(sessionId, "new-chat", "", 0, now);
    }

    public List<AgentSessionSummaryResponse> listSessions(AuthenticatedUser user) {
        return conversationStore.list(user.userId()).stream()
                .map(session -> new AgentSessionSummaryResponse(
                        session.sessionId(),
                        session.title(),
                        session.preview(),
                        session.messageCount(),
                        session.updatedAt()))
                .toList();
    }

    public AgentSessionDetailResponse getSession(AuthenticatedUser user, String sessionId) {
        AgentSessionContext sessionContext = conversationStore.load(user.userId(), sessionId)
                .orElseThrow(() -> new AuthException("Conversation session not found"));
        return new AgentSessionDetailResponse(
                sessionContext.sessionId(),
                buildTitle(sessionContext.messages()),
                sessionContext.messages().stream()
                        .map(message -> new AgentConversationMessageResponse(message.role(), message.content()))
                        .toList(),
                sessionContext.createdAt(),
                sessionContext.updatedAt());
    }

    public void deleteSession(AuthenticatedUser user, String sessionId) {
        conversationStore.delete(user.userId(), normalizeSessionId(sessionId));
    }

    private String callModel(List<AgentMessage> history) {
        List<Message> messages = toChatMessages(promptProvider.systemPrompt(), history);
        log.debug("Calling chat model: model={}, sessionMessageCount={}, toolEnabled={}",
                model,
                messages.size(),
                agentProperties.getTool().isEnabled());
        ChatResponse response = chatClient.prompt()
                .messages(messages)
                .call()
                .chatResponse();
        log.info("Chat model completed: model={}, hasToolCalls={}, generationCount={}",
                model,
                response.hasToolCalls(),
                response.getResults().size());
        return response.getResult().getOutput().getText();
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private List<AgentMessage> boundedHistory(List<AgentMessage> messages) {
        int max = Math.max(2, agentProperties.getMaxHistoryMessages());
        if (messages.size() <= max) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(messages.size() - max, messages.size()));
    }

    private List<Message> toChatMessages(String systemPrompt, List<AgentMessage> conversation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (AgentMessage message : conversation) {
            messages.add(toChatMessage(message));
        }
        return messages;
    }

    private Message toChatMessage(AgentMessage message) {
        if ("assistant".equalsIgnoreCase(message.role())) {
            return new AssistantMessage(message.content());
        }
        if (StringUtils.hasText(message.role()) && !"user".equalsIgnoreCase(message.role())) {
            return new SystemMessage(message.content());
        }
        return new UserMessage(message.content());
    }

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .findFirst()
                .orElse("new-chat");
    }
}
