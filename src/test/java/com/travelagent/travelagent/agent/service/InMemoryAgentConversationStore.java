package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.model.AgentSessionSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryAgentConversationStore implements AgentConversationStore {

    private final ConcurrentMap<String, AgentSessionContext> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentSessionContext> load(long userId, String sessionId) {
        return Optional.ofNullable(sessions.get(key(userId, sessionId)));
    }

    @Override
    public void save(long userId, AgentSessionContext sessionContext) {
        sessions.put(key(userId, sessionContext.sessionId()), sessionContext);
    }

    @Override
    public void append(long userId, AgentSessionContext sessionContext, List<AgentMessage> messages) {
        sessions.compute(key(userId, sessionContext.sessionId()), (ignored, existing) -> new AgentSessionContext(
                sessionContext.sessionId(), sessionContext.messages(),
                existing == null ? sessionContext.createdAt() : existing.createdAt(), sessionContext.updatedAt()));
    }

    @Override
    public List<AgentSessionSummary> list(long userId) {
        String prefix = userId + ":";
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> toSummary(entry.getValue()))
                .sorted(Comparator.comparing(AgentSessionSummary::updatedAt).reversed())
                .toList();
    }

    @Override
    public void delete(long userId, String sessionId) {
        sessions.remove(key(userId, sessionId));
    }

    private AgentSessionSummary toSummary(AgentSessionContext sessionContext) {
        String title = sessionContext.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(AgentMessage::content)
                .findFirst()
                .orElse("new-chat");
        String preview = sessionContext.messages().isEmpty() ? "" : sessionContext.messages().getLast().content();
        return new AgentSessionSummary(sessionContext.sessionId(), title, preview, sessionContext.messages().size(), sessionContext.updatedAt());
    }

    private String key(long userId, String sessionId) {
        return userId + ":" + sessionId;
    }
}
