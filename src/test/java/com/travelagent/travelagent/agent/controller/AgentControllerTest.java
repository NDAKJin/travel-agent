package com.travelagent.travelagent.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentConversationMessageResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.service.DefaultReactAgentService;
import com.travelagent.travelagent.agent.service.NearbyPoiSearchService;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentControllerTest {

    private static final UsernamePasswordAuthenticationToken AUTHENTICATION =
            new UsernamePasswordAuthenticationToken(new AuthenticatedUser(1L, "admin", "admin", "ops-admin"), "token");

    private MockMvc mockMvc;
    private DefaultReactAgentService reactAgentService;

    @BeforeEach
    void setUp() {
        reactAgentService = mock(DefaultReactAgentService.class);
        AgentController agentController = new AgentController(reactAgentService, mock(NearbyPoiSearchService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(agentController).build();
    }

    @Test
    void chatReturnsAgentResponse() throws Exception {
        when(reactAgentService.chat(any(), any())).thenReturn(new AgentChatResponse("session-1", "Start with West Lake", "Travel Buddy", "qwen-plus", false));

        mockMvc.perform(post("/api/agent/chat")
                        .principal(AUTHENTICATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Help me plan one day in Hangzhou",
                                  "sessionId": "session-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.reply").value("Start with West Lake"))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.toolEnabled").value(false));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .principal(AUTHENTICATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": " ",
                                  "sessionId": "session-1"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSessionsReturnsCurrentUsersHistory() throws Exception {
        when(reactAgentService.listSessions(any())).thenReturn(List.of(
                new AgentSessionSummaryResponse("session-1", "Hangzhou trip", "Then visit Lingyin Temple", 4, null)));

        mockMvc.perform(get("/api/agent/sessions").principal(AUTHENTICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$[0].title").value("Hangzhou trip"))
                .andExpect(jsonPath("$[0].messageCount").value(4));
    }

    @Test
    void createSessionReturnsCreatedSession() throws Exception {
        when(reactAgentService.createSession(any())).thenReturn(
                new AgentSessionSummaryResponse("session-1", "新对话", "", 0, null));

        mockMvc.perform(post("/api/agent/sessions").principal(AUTHENTICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.title").value("新对话"))
                .andExpect(jsonPath("$.messageCount").value(0));
    }

    @Test
    void getSessionReturnsConversationDetail() throws Exception {
        when(reactAgentService.getSession(any(), any())).thenReturn(new AgentSessionDetailResponse(
                "session-1",
                "Hangzhou trip",
                List.of(
                        new AgentConversationMessageResponse("user", "Plan a Hangzhou trip"),
                        new AgentConversationMessageResponse("assistant", "Start with West Lake")),
                null,
                null));

        mockMvc.perform(get("/api/agent/sessions/session-1").principal(AUTHENTICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Start with West Lake"));
    }
}
