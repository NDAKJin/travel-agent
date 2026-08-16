package com.travelagent.travelagent.agent.mapper;

import com.travelagent.travelagent.agent.model.AgentObservationLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentObservationLogMapper {
    int insertIgnore(AgentObservationLog log);

    List<AgentObservationLog> findBySessionId(long sessionId);
}
