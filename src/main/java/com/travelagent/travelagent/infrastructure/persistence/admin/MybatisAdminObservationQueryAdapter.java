package com.travelagent.travelagent.infrastructure.persistence.admin;

import com.travelagent.travelagent.application.admin.port.out.AdminObservationQueryPort;
import com.travelagent.travelagent.domain.agent.model.AgentObservationLog;
import com.travelagent.travelagent.infrastructure.persistence.agent.AgentObservationLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisAdminObservationQueryAdapter implements AdminObservationQueryPort {
    private final AgentObservationLogMapper mapper;
    public List<AgentObservationLog> findBySessionId(long sessionId) { return mapper.findBySessionId(sessionId); }
}
