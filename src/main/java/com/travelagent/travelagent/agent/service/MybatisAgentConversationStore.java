package com.travelagent.travelagent.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.agent.mapper.AgentConversationSessionMapper;
import com.travelagent.travelagent.agent.model.AgentConversationSession;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.model.AgentSessionSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MybatisAgentConversationStore implements AgentConversationStore {

    private static final TypeReference<List<AgentMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final AgentConversationSessionMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisAgentConversationStore(AgentConversationSessionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AgentSessionContext> load(long userId, String sessionId) {
        return Optional.ofNullable(mapper.findByUserIdAndSessionId(userId, sessionId)).map(this::toContext);
    }

    @Override
    public void save(long userId, AgentSessionContext sessionContext) {
        AgentConversationSession session = Optional.ofNullable(
                mapper.findByUserIdAndSessionId(userId, sessionContext.sessionId()))
                .orElseGet(AgentConversationSession::new);
        session.setUserId(userId);
        session.setSessionId(sessionContext.sessionId());
        session.setTitle(buildTitle(sessionContext.messages()));
        session.setPreview(buildPreview(sessionContext.messages()));
        session.setMessageCount(sessionContext.messages().size());
        session.setMessagesJson(serialize(sessionContext.messages()));
        session.setCreatedAt(sessionContext.createdAt());
        session.setUpdatedAt(sessionContext.updatedAt());
        if (session.getId() == null) {
            mapper.insert(session);
        } else {
            mapper.update(session);
        }
    }

    @Override
    public List<AgentSessionSummary> list(long userId) {
        return mapper.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> new AgentSessionSummary(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getPreview(),
                        session.getMessageCount(),
                        session.getUpdatedAt()))
                .toList();
    }

    @Override
    public void delete(long userId, String sessionId) {
        mapper.deleteByUserIdAndSessionId(userId, sessionId);
    }

    private AgentSessionContext toContext(AgentConversationSession session) {
        return new AgentSessionContext(
                session.getSessionId(),
                deserialize(session.getMessagesJson()),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .map(this::compact)
                .filter(text -> !text.isBlank())
                .findFirst()
                .map(text -> truncate(text, 60))
                .orElse("新对话");
    }

    private String buildPreview(List<AgentMessage> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        return truncate(compact(messages.getLast().content()), 120);
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String serialize(List<AgentMessage> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize conversation messages", ex);
        }
    }

    private List<AgentMessage> deserialize(String messagesJson) {
        try {
            return objectMapper.readValue(messagesJson, MESSAGE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize conversation messages", ex);
        }
    }
}
