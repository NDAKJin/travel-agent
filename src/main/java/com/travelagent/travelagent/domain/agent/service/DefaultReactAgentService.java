package com.travelagent.travelagent.domain.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.domain.agent.model.AgentLocationPayload;
import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.agent.model.AgentMessageRole;
import com.travelagent.travelagent.domain.agent.model.AgentSessionContext;
import com.travelagent.travelagent.domain.auth.exception.AuthException;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationPort;
import com.travelagent.travelagent.infrastructure.ai.TokenCounter;
import com.travelagent.travelagent.infrastructure.config.AgentProperties;
import com.travelagent.travelagent.infrastructure.planning.port.ConversationStorePort;
import com.travelagent.travelagent.infrastructure.planning.port.TravelWorkflowPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 对话领域服务，负责聊天编排以及会话的查询和维护。
 *
 * 该服务同时协调会话存储、旅行工作流、上下文裁剪和摘要生成，
 * 对外提供稳定的 Agent 会话操作入口。
 */
@Service
@Slf4j
public class DefaultReactAgentService {
    private static final String NEW_CHAT_TITLE = "new-chat";
    private static final String QUESTIONS_PREFIX = "questions:";
    private static final String SUMMARY_PREFIX = "Conversation summary:\n";
    private static final int MESSAGE_TOKEN_OVERHEAD = 4;

    private final AgentProperties agentProperties;
    private final TravelWorkflowPort agentGraph;
    private final ConversationStorePort conversationStore;
    private final AgentObservationPort observationPublisher;
    private final String model;
    private final TokenCounter tokenCounter;
    private final ChatClient summaryChatClient;

    public DefaultReactAgentService(AgentProperties agentProperties,
                                    TravelWorkflowPort agentGraph,
                                    ConversationStorePort conversationStore,
                                    AgentObservationPort observationPublisher,
                                    @Qualifier("finalizerChatClient") ChatClient summaryChatClient,
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

    /**
     * 处理一次用户对话请求，并持久化用户消息和 Agent 回复。
     *
     * @param user 当前认证用户
     * @param request 对话请求，包含消息、会话标识和可选位置
     * @return Agent 回复及会话信息
     */
    public AgentChatResponse chat(AuthenticatedUser user, AgentChatRequest request) {
        validateRequest(request);
        String sessionId = normalizeSessionId(request.sessionId());
        log.info("Starting react-agent chat: sessionId={}, requestedSessionId={}",
                sessionId,
                request.sessionId());
        Optional<AgentSessionContext> existingSession = conversationStore.load(user.userId(), sessionId);
        List<AgentMessage> fullHistory = new ArrayList<>(
                existingSession.map(AgentSessionContext::messages).orElseGet(List::of));
        String summary = existingSession.map(AgentSessionContext::summary).orElse("");
        log.debug("Loaded conversation history: sessionId={}, existingMessageCount={}",
                sessionId, fullHistory.size());

        fullHistory.add(new AgentMessage(AgentMessageRole.USER, request.message()));
        List<AgentMessage> history = boundedHistory(fullHistory);
        if (summaryChatClient != null && shouldSummarize(fullHistory)) {
            summary = summarize(fullHistory, summary);
            history = contextHistory(summary, fullHistory);
        }
        history = new ArrayList<>(history);

        Instant startedAt = Instant.now();
        Instant createdAt = existingSession.map(AgentSessionContext::createdAt).orElse(startedAt);
        long messageId = conversationStore.append(user.userId(),
                new AgentSessionContext(sessionId, boundedHistory(history), createdAt, startedAt, summary),
                List.of(new AgentMessage(AgentMessageRole.USER, request.message()))).getLast();

        AgentObservationContext observation = new AgentObservationContext(messageId, observationPublisher);
        String reply = callModel(history, user.userId() + ":" + sessionId, observation, request.location());
        String userReply = userFacingReply(reply);
        history.add(new AgentMessage(AgentMessageRole.ASSISTANT, userReply));
        fullHistory.add(new AgentMessage(AgentMessageRole.ASSISTANT, userReply));
        Instant now = Instant.now();
        AgentSessionContext updatedSession = new AgentSessionContext(
                sessionId, boundedHistory(fullHistory), createdAt, now, summary);
        conversationStore.append(user.userId(), updatedSession,
                List.of(new AgentMessage(AgentMessageRole.ASSISTANT, userReply)));
        conversationStore.save(user.userId(), new AgentSessionContext(
                sessionId, fullHistory, createdAt, now, summary));
        log.info("Completed react-agent chat: sessionId={}, totalMessageCount={}, replyLength={}",
                sessionId, history.size(), userReply.length());
        return new AgentChatResponse(sessionId, userReply,
                agentProperties.getProfile().getName(), model);
    }

    private void validateRequest(AgentChatRequest request) {
        if (request.message().length() > agentProperties.getMaxMessageChars()) {
            throw new IllegalArgumentException("message exceeds the configured maximum length");
        }
        if (request.sessionId() != null
                && request.sessionId().length() > agentProperties.getMaxSessionIdChars()) {
            throw new IllegalArgumentException("sessionId exceeds the configured maximum length");
        }
    }

    /**
     * 为用户创建一个空的对话会话。
     *
     * @param user 当前认证用户
     * @return 新会话摘要
     */
    public AgentSessionSummaryResponse createSession(AuthenticatedUser user) {
        Instant now = Instant.now();
        String sessionId = UUID.randomUUID().toString();
        conversationStore.save(user.userId(), new AgentSessionContext(sessionId, List.of(), now, now));
        return new AgentSessionSummaryResponse(sessionId, NEW_CHAT_TITLE, "", 0, now);
    }

    /**
     * 查询当前用户拥有的全部对话会话。
     *
     * @param user 当前认证用户
     * @return 会话摘要列表
     */
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

    /**
     * 查询指定会话的详细消息记录。
     *
     * @param user 当前认证用户
     * @param sessionId 会话标识
     * @return 会话详情
     * @throws AuthException 会话不存在或不属于当前用户时抛出
     */
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

    /**
     * 删除用户会话及其对应的工作流状态。
     *
     * @param user 当前认证用户
     * @param sessionId 会话标识
     */
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
        return JSON.toJSONString(AgentLocationPayload.from(
                location.latitude(), location.longitude(), location.accuracy()),
                JSONWriter.Feature.WriteMapNullValue);
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
        return trimmed.regionMatches(true, 0, QUESTIONS_PREFIX, 0, QUESTIONS_PREFIX.length())
                ? trimmed.substring(QUESTIONS_PREFIX.length()).trim() : reply;
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
        LinkedList<AgentMessage> result = new LinkedList<>();
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
        return message == null ? MESSAGE_TOKEN_OVERHEAD : tokenCounter.count(message.content()) + MESSAGE_TOKEN_OVERHEAD;
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
        result.add(new AgentMessage(AgentMessageRole.SYSTEM, SUMMARY_PREFIX + summary));
        result.addAll(recent);
        return boundedHistory(result);
    }

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> AgentMessageRole.USER.matches(message.role()))
                .map(AgentMessage::content)
                .findFirst()
                .orElse(NEW_CHAT_TITLE);
    }
}
