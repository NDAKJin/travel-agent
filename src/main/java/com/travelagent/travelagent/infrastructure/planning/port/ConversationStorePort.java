package com.travelagent.travelagent.infrastructure.planning.port;

import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.agent.model.AgentSessionContext;
import com.travelagent.travelagent.domain.agent.model.AgentSessionSummary;
import java.util.List;
import java.util.Optional;

/** 会话持久化端口，数据库只是它的一个适配器。 */
public interface ConversationStorePort {

    Optional<AgentSessionContext> load(long userId, String sessionId);

    void save(long userId, AgentSessionContext sessionContext);

    List<Long> append(long userId, AgentSessionContext sessionContext, List<AgentMessage> messages);

    List<AgentSessionSummary> list(long userId);

    void delete(long userId, String sessionId);
}
