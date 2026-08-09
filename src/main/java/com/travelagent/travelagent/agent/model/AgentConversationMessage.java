package com.travelagent.travelagent.agent.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConversationMessage {

    private Long id;
    private Long sessionId;
    private int sequenceNo;
    private String role;
    private String content;
    private Instant createdAt;
}
