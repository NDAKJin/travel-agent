package com.travelagent.travelagent.infrastructure.persistence.agent;

import com.travelagent.travelagent.domain.agent.model.AgentObservationLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentObservationLogMapper {
    int insertIgnore(AgentObservationLog log);

    List<AgentObservationLog> findBySessionId(long sessionId);
}
