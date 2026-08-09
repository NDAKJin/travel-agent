package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.mapper.AgentConversationSessionMapper;
import com.travelagent.travelagent.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.agent.model.AgentConversationSession;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.model.AgentSessionSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MybatisAgentConversationStore implements AgentConversationStore {

    private final AgentConversationSessionMapper mapper;

    public MybatisAgentConversationStore(AgentConversationSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentSessionContext> load(long userId, String sessionId) {
        return Optional.ofNullable(mapper.findByUserIdAndSessionId(userId, sessionId))
                .map(session -> toContext(session, mapper.findMessagesBySessionId(session.getId())));
    }

    @Transactional
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
        session.setCreatedAt(sessionContext.createdAt());
        session.setUpdatedAt(sessionContext.updatedAt());
        if (session.getId() == null) {
            mapper.insert(session);
        } else {
            mapper.update(session);
        }

        mapper.deleteMessagesBySessionId(session.getId());
        if (!sessionContext.messages().isEmpty()) {
            mapper.insertMessages(toMessageEntities(session.getId(), sessionContext.messages()));
        }
    }

    @Transactional
    @Override
    public void append(long userId, AgentSessionContext sessionContext, List<AgentMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        AgentConversationSession session = mapper.findByUserIdAndSessionIdForUpdate(
                userId, sessionContext.sessionId());
        if (session == null) {
            session = new AgentConversationSession();
            session.setUserId(userId);
            session.setSessionId(sessionContext.sessionId());
            session.setTitle(buildTitle(sessionContext.messages()));
            session.setCreatedAt(sessionContext.createdAt());
            session.setMessageCount(0);
            session.setPreview("");
            session.setUpdatedAt(sessionContext.updatedAt());
            mapper.insert(session);
        }

        List<AgentMessage> boundedMessages = sessionContext.messages();
        session.setTitle(session.getTitle() == null || session.getTitle().isBlank()
                || "new-chat".equals(session.getTitle())
                ? buildTitle(sessionContext.messages()) : session.getTitle());
        session.setPreview(buildPreview(messages));
        session.setMessageCount(boundedMessages.size());
        session.setUpdatedAt(sessionContext.updatedAt());
        mapper.update(session);
        mapper.deleteMessagesBySessionId(session.getId());
        if (!boundedMessages.isEmpty()) {
            mapper.insertMessages(toMessageEntities(session.getId(), 0, boundedMessages));
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

    @Transactional
    @Override
    public void delete(long userId, String sessionId) {
        mapper.deleteByUserIdAndSessionId(userId, sessionId);
    }

    private AgentSessionContext toContext(AgentConversationSession session,
                                          List<AgentConversationMessage> messages) {
        return new AgentSessionContext(
                session.getSessionId(),
                messages.stream()
                        .map(message -> new AgentMessage(message.getRole(), message.getContent()))
                        .toList(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private List<AgentConversationMessage> toMessageEntities(long sessionId, List<AgentMessage> messages) {
        return toMessageEntities(sessionId, 0, messages);
    }

    private List<AgentConversationMessage> toMessageEntities(long sessionId, int firstSequenceNo,
                                                              List<AgentMessage> messages) {
        Instant createdAt = Instant.now();
        return IntStream.range(0, messages.size()).mapToObj(index -> {
            AgentMessage source = messages.get(index);
            AgentConversationMessage target = new AgentConversationMessage();
            target.setSessionId(sessionId);
            target.setSequenceNo(firstSequenceNo + index);
            target.setRole(source.role());
            target.setContent(source.content());
            target.setCreatedAt(createdAt);
            return target;
        }).toList();
    }

    private String buildTitle(List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .map(this::compact)
                .filter(text -> !text.isBlank())
                .findFirst()
                .map(text -> truncate(text, 60))
                .orElse("new-chat");
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
}
