package com.travelagent.travelagent.application.agent;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.service.DefaultReactAgentService;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import org.junit.jupiter.api.Test;

class AgentApplicationTest {
    @Test void delegatesAgentOperations() {
        DefaultReactAgentService service = mock(DefaultReactAgentService.class);
        AgentApplication app = new AgentApplication(service);
        AuthenticatedUser user = new AuthenticatedUser(1L, "wx", "s", "n");
        AgentChatRequest request = mock(AgentChatRequest.class);
        app.chat(user, request); app.createSession(user); app.listSessions(user); app.getSession(user, "id"); app.deleteSession(user, "id");
        verify(service).chat(user, request); verify(service).createSession(user); verify(service).listSessions(user);
        verify(service).getSession(user, "id"); verify(service).deleteSession(user, "id");
    }
}
