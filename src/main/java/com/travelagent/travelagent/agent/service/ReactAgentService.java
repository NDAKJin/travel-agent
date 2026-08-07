package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import java.util.List;

public interface ReactAgentService {

    AgentChatResponse chat(AuthenticatedUser user, AgentChatRequest request);

    AgentSessionSummaryResponse createSession(AuthenticatedUser user);

    List<AgentSessionSummaryResponse> listSessions(AuthenticatedUser user);

    AgentSessionDetailResponse getSession(AuthenticatedUser user, String sessionId);

    void deleteSession(AuthenticatedUser user, String sessionId);
}
