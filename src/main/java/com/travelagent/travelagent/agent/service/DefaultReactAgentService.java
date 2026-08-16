package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import com.travelagent.travelagent.config.AgentProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
public class DefaultReactAgentService {

    private final AgentProperties agentProperties;
    private final LangGraphTravelAgent agentGraph;
    private final AgentConversationStore conversationStore;
    private final String model;

    public DefaultReactAgentService(AgentProperties agentProperties,
                                    LangGraphTravelAgent agentGraph,
                                    AgentConversationStore conversationStore,
                                    @Value("${SPRING_AI_DASHSCOPE_CHAT_OPTIONS_MODEL:qwen3.7-flash}") String model) {
        this.agentProperties = agentProperties;
        this.agentGraph = agentGraph;
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
        log.debug("Calling chat model: model={}, sessionMessageCount={}, toolEnabled={}",
                model,
                history.size(),
                agentProperties.getTool().isEnabled());
        return agentGraph.run(history);
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

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .findFirst()
                .orElse("new-chat");
    }
}
