package com.travelagent.travelagent.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.observation.AgentObservationPublisher;
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
    private final AgentObservationPublisher observationPublisher;
    private final String model;

    public DefaultReactAgentService(AgentProperties agentProperties,
                                    LangGraphTravelAgent agentGraph,
                                    AgentConversationStore conversationStore,
                                    AgentObservationPublisher observationPublisher,
                                    @Value("${SPRING_AI_DASHSCOPE_CHAT_OPTIONS_MODEL:qwen3.7-flash}") String model) {
        this.agentProperties = agentProperties;
        this.agentGraph = agentGraph;
        this.conversationStore = conversationStore;
        this.observationPublisher = observationPublisher;
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

            Instant startedAt = Instant.now();
            Instant createdAt = existingSession == null ? startedAt : existingSession.createdAt();
            List<AgentMessage> userHistory = boundedHistory(history);
            long messageId = conversationStore.append(user.userId(),
                    new AgentSessionContext(sessionId, userHistory, createdAt, startedAt),
                    List.of(new AgentMessage("user", request.message()))).getLast();

            AgentObservationContext observation = new AgentObservationContext(messageId, observationPublisher);
            String reply = callModel(history, user.userId() + ":" + sessionId, observation, request.location());
            String userReply = userFacingReply(reply);
            history.add(new AgentMessage("assistant", userReply));
            Instant now = Instant.now();
            List<AgentMessage> storedHistory = boundedHistory(history);
            conversationStore.append(user.userId(),
                    new AgentSessionContext(sessionId, storedHistory, createdAt, now),
                    List.of(new AgentMessage("assistant", userReply)));
            log.info("Completed react-agent chat: sessionId={}, totalMessageCount={}, replyLength={}",
                    sessionId,
                    history.size(),
                    userReply.length());
            return new AgentChatResponse(
                    sessionId,
                    userReply,
                    agentProperties.getProfile().getName(),
                    model,
                    agentProperties.getTool().isEnabled());
        }
        finally {
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
        String normalizedSessionId = normalizeSessionId(sessionId);
        agentGraph.clear(user.userId() + ":" + normalizedSessionId);
        conversationStore.delete(user.userId(), normalizedSessionId);
    }

    private String callModel(List<AgentMessage> history, String conversationId,
                             AgentObservationContext observation, AgentChatRequest.Location location) {
        log.debug("Calling chat model: model={}, sessionMessageCount={}, toolEnabled={}",
                model,
                history.size(),
                agentProperties.getTool().isEnabled());
        String locationJson = locationJson(location);
        return locationJson == null
                ? agentGraph.run(history, conversationId, observation)
                : agentGraph.run(history, conversationId, observation, locationJson);
    }

    private String locationJson(AgentChatRequest.Location location) {
        if (location == null) return null;
        java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("latitude", location.latitude());
        value.put("longitude", location.longitude());
        value.put("accuracy", location.accuracy());
        value.put("updatedAt", Instant.now().toString());
        return JSON.toJSONString(value, JSONWriter.Feature.WriteMapNullValue);
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private String userFacingReply(String reply) {
        if (reply == null) return null;
        String trimmed = reply.trim();
        return trimmed.regionMatches(true, 0, "questions:", 0, "questions:".length())
                ? trimmed.substring("questions:".length()).trim() : reply;
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
