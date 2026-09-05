package com.travelagent.travelagent.domain.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.agent.model.AgentSessionContext;
import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationPort;
import com.travelagent.travelagent.domain.auth.exception.AuthException;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import com.travelagent.travelagent.infrastructure.planning.port.TravelWorkflowPort;
import com.travelagent.travelagent.infrastructure.planning.port.ConversationStorePort;
import com.travelagent.travelagent.infrastructure.config.AgentProperties;
import com.travelagent.travelagent.infrastructure.ai.TokenCounter;
import org.springframework.ai.chat.client.ChatClient;
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
    private final TravelWorkflowPort agentGraph;
    private final ConversationStorePort conversationStore;
    private final AgentObservationPort observationPublisher;
    private final String model;
    private final TokenCounter tokenCounter;
    private final ChatClient summaryChatClient;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultReactAgentService(AgentProperties agentProperties,
                                    TravelWorkflowPort agentGraph,
                                    ConversationStorePort conversationStore,
                                    AgentObservationPort observationPublisher,
                                    @org.springframework.beans.factory.annotation.Qualifier("finalizerChatClient") ChatClient summaryChatClient,
                                    TokenCounter tokenCounter,
                                    @Value("${SPRING_AI_DASHSCOPE_CHAT_OPTIONS_MODEL:qwen3.7-flash}") String model) {
        this.agentProperties = agentProperties;
        this.agentGraph = agentGraph;
        this.conversationStore = conversationStore;
        this.observationPublisher = observationPublisher;
        this.summaryChatClient = summaryChatClient;
        this.tokenCounter = tokenCounter;
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
        log.info("Starting react-agent chat: sessionId={}, requestedSessionId={}",
                sessionId,
                request.sessionId());
            AgentSessionContext existingSession = conversationStore.load(user.userId(), sessionId).orElse(null);
            List<AgentMessage> fullHistory = new ArrayList<>(existingSession == null ? List.of() : existingSession.messages());
            String summary = existingSession == null ? "" : existingSession.summary();
            List<AgentMessage> history = new ArrayList<>(fullHistory);
            log.debug("Loaded conversation history: sessionId={}, existingMessageCount={}", sessionId, history.size());
            history.add(new AgentMessage("user", request.message()));
            fullHistory.add(new AgentMessage("user", request.message()));
            if (summaryChatClient != null && shouldSummarize(fullHistory)) {
                summary = summarize(fullHistory, summary);
                history = new ArrayList<>(contextHistory(summary, fullHistory));
            } else {
                history = boundedHistory(fullHistory);
            }
            history = new ArrayList<>(history);

            Instant startedAt = Instant.now();
            Instant createdAt = existingSession == null ? startedAt : existingSession.createdAt();
            List<AgentMessage> userHistory = boundedHistory(history);
            long messageId = conversationStore.append(user.userId(),
                    new AgentSessionContext(sessionId, userHistory, createdAt, startedAt, summary),
                    List.of(new AgentMessage("user", request.message()))).getLast();

            AgentObservationContext observation = new AgentObservationContext(messageId, observationPublisher);
            String reply = callModel(history, user.userId() + ":" + sessionId, observation, request.location());
            String userReply = userFacingReply(reply);
            history.add(new AgentMessage("assistant", userReply));
            fullHistory.add(new AgentMessage("assistant", userReply));
            Instant now = Instant.now();
            conversationStore.append(user.userId(),
                    new AgentSessionContext(sessionId, boundedHistory(fullHistory), createdAt, now, summary),
                    List.of(new AgentMessage("assistant", userReply)));
            conversationStore.save(user.userId(), new AgentSessionContext(sessionId, fullHistory, createdAt, now, summary));
            log.info("Completed react-agent chat: sessionId={}, totalMessageCount={}, replyLength={}",
                    sessionId,
                    history.size(),
                    userReply.length());
            return new AgentChatResponse(
                    sessionId,
                    userReply,
                    agentProperties.getProfile().getName(),
                    model);
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
        log.debug("Calling chat model: model={}, sessionMessageCount={}",
                model,
                history.size());
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

    /**
     * Keeps the newest messages that fit the history token budget. The database
     * still contains the complete conversation; this only controls what is sent
     * to the model. A lightweight character estimate avoids adding a tokenizer
     * dependency and is intentionally conservative for mixed Chinese/English.
     */
    private List<AgentMessage> boundedHistory(List<AgentMessage> messages) {
        int budget = Math.max(256, agentProperties.getMaxHistoryTokens());
        int used = 0;
        java.util.LinkedList<AgentMessage> result = new java.util.LinkedList<>();
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentMessage message = messages.get(index);
            int messageTokens = estimateTokens(message);
            if (used + messageTokens <= budget) {
                result.addFirst(message);
                used += messageTokens;
                continue;
            }
            break;
        }
        return List.copyOf(result);
    }

    private int estimateTokens(AgentMessage message) {
        return message == null ? 4 : tokenCounter.count(message.content()) + 4;
    }

    private boolean shouldSummarize(List<AgentMessage> messages) {
        int total = messages.stream().mapToInt(this::estimateTokens).sum();
        return total >= agentProperties.getMaxHistoryTokens() * agentProperties.getSummaryTriggerRatio();
    }

    private String summarize(List<AgentMessage> messages, String previous) {
        int keep = Math.max(256, agentProperties.getRecentMessageTokens());
        int used = 0, split = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int t = estimateTokens(messages.get(i));
            if (used + t > keep) { split = i + 1; break; }
            used += t;
        }
        if (split <= 0) return previous == null ? "" : previous;
        StringBuilder input = new StringBuilder();
        if (previous != null && !previous.isBlank()) input.append("既有摘要：\n").append(previous).append("\n");
        messages.subList(0, split).forEach(m -> input.append(m.role()).append(": ").append(m.content()).append("\n"));
        String prompt = "请将以下旅行对话压缩成结构化摘要，保留用户偏好、约束、已确认事实、未解决问题和关键决策。只输出摘要，控制在 "
                + agentProperties.getSummaryTargetTokens() + " token 内。\n" + input;
        String result = summaryChatClient.prompt().user(prompt).call().content();
        return result == null ? (previous == null ? "" : previous) : result.trim();
    }

    private List<AgentMessage> contextHistory(String summary, List<AgentMessage> messages) {
        List<AgentMessage> recent = boundedHistory(messages);
        if (summary == null || summary.isBlank()) return recent;
        List<AgentMessage> result = new ArrayList<>();
        result.add(new AgentMessage("system", "Conversation summary:\n" + summary));
        result.addAll(recent);
        return boundedHistory(result);
    }

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .findFirst()
                .orElse("new-chat");
    }
}
