package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 数据库中的 Agent 会话消息实体。 */
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
