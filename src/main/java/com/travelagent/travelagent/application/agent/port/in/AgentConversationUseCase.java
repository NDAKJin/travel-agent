package com.travelagent.travelagent.application.agent.port.in;

import com.travelagent.travelagent.application.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.application.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.application.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.application.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.application.auth.model.AuthenticatedUser;
import java.util.List;

public interface AgentConversationUseCase {

    AgentChatResponse chat(AuthenticatedUser user, AgentChatRequest request);

    AgentSessionSummaryResponse createSession(AuthenticatedUser user);

    List<AgentSessionSummaryResponse> listSessions(AuthenticatedUser user);

    AgentSessionDetailResponse getSession(AuthenticatedUser user, String sessionId);

    void deleteSession(AuthenticatedUser user, String sessionId);
}
