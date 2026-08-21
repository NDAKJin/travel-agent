package com.travelagent.travelagent.application.admin.port.out;

import com.travelagent.travelagent.domain.agent.model.AgentObservationLog;
import java.util.List;

public interface AdminObservationQueryPort {
    List<AgentObservationLog> findBySessionId(long sessionId);
}
