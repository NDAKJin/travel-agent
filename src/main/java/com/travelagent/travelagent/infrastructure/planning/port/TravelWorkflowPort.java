package com.travelagent.travelagent.infrastructure.planning.port;

import com.travelagent.travelagent.domain.agent.model.AgentMessage;
import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import java.util.List;

/**
 * 旅行对话工作流端口。LangGraph4j 只是该端口的一种实现。
 */
public interface TravelWorkflowPort {

    default String run(List<AgentMessage> history, String conversationId,
                       AgentObservationContext observation) {
        return run(history, conversationId, observation, null);
    }

    String run(List<AgentMessage> history, String conversationId,
               AgentObservationContext observation, String currentUserLocation);

    void clear(String conversationId);
}
