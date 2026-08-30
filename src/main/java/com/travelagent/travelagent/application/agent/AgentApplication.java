package com.travelagent.travelagent.application.agent;

import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.domain.agent.service.DefaultReactAgentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Agent application facade. Controllers depend on this facade only. */
@Service
@RequiredArgsConstructor
public class AgentApplication {

    private final DefaultReactAgentService service;

    public AgentChatResponse chat(AuthenticatedUser user, AgentChatRequest request) {
        return service.chat(user, request);
    }

    public AgentSessionSummaryResponse createSession(AuthenticatedUser user) {
        return service.createSession(user);
    }

    public List<AgentSessionSummaryResponse> listSessions(AuthenticatedUser user) {
        return service.listSessions(user);
    }

    public AgentSessionDetailResponse getSession(AuthenticatedUser user, String sessionId) {
        return service.getSession(user, sessionId);
    }

    public void deleteSession(AuthenticatedUser user, String sessionId) {
        service.deleteSession(user, sessionId);
    }
}
