package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelagent.travelagent.agent.mapper.AgentConversationSessionMapper;
import com.travelagent.travelagent.agent.model.AgentConversationMessage;
import com.travelagent.travelagent.agent.model.AgentConversationSession;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.model.AgentSessionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MybatisAgentConversationStoreTest {

    @Mock
    private AgentConversationSessionMapper mapper;

    @InjectMocks
    private MybatisAgentConversationStore store;

    @Test
    void savePersistsSessionMetadataAndMessagesSeparately() {
        Instant now = Instant.now();
        doAnswer(invocation -> {
            AgentConversationSession session = invocation.getArgument(0);
            session.setId(42L);
            return 1;
        }).when(mapper).insert(any(AgentConversationSession.class));

        store.save(7L, new AgentSessionContext("session-1", List.of(
                new AgentMessage("user", "Plan a trip"),
                new AgentMessage("assistant", "Start with the lake")), now, now));

        ArgumentCaptor<List<AgentConversationMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).deleteMessagesBySessionId(42L);
        verify(mapper).insertMessages(captor.capture());
        assertThat(captor.getValue()).extracting(AgentConversationMessage::getSessionId)
                .containsExactly(42L, 42L);
        assertThat(captor.getValue()).extracting(AgentConversationMessage::getSequenceNo)
                .containsExactly(0, 1);
        assertThat(captor.getValue()).extracting(AgentConversationMessage::getContent)
                .containsExactly("Plan a trip", "Start with the lake");
    }

    @Test
    void loadReconstructsConversationFromMessageRows() {
        AgentConversationSession session = new AgentConversationSession();
        session.setId(42L);
        session.setUserId(7L);
        session.setSessionId("session-1");
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(session.getCreatedAt());
        when(mapper.findByUserIdAndSessionId(7L, "session-1")).thenReturn(session);

        AgentConversationMessage userMessage = message(42L, 0, "user", "Plan a trip");
        AgentConversationMessage assistantMessage = message(42L, 1, "assistant", "Start with the lake");
        when(mapper.findMessagesBySessionId(42L)).thenReturn(List.of(userMessage, assistantMessage));

        Optional<AgentSessionContext> loaded = store.load(7L, "session-1");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().messages())
                .containsExactly(new AgentMessage("user", "Plan a trip"),
                        new AgentMessage("assistant", "Start with the lake"));
    }

    private AgentConversationMessage message(long sessionId, int sequenceNo, String role, String content) {
        AgentConversationMessage message = new AgentConversationMessage();
        message.setSessionId(sessionId);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
