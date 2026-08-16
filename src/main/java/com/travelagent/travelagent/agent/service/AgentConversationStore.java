package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.model.AgentSessionContext;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionSummary;
import java.util.List;
import java.util.Optional;

public interface AgentConversationStore {

    Optional<AgentSessionContext> load(long userId, String sessionId);

    void save(long userId, AgentSessionContext sessionContext);

    List<Long> append(long userId, AgentSessionContext sessionContext, List<AgentMessage> messages);

    List<AgentSessionSummary> list(long userId);

    void delete(long userId, String sessionId);
}
