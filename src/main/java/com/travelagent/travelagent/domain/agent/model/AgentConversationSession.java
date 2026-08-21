package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConversationSession {

    private Long id;
    private Long userId;
    private String sessionId;
    private String title;
    private String preview;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
