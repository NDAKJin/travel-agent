package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;
import java.util.List;

/** Agent 会话上下文值对象。 */
public record AgentSessionContext(String sessionId, List<AgentMessage> messages,
                                  Instant createdAt, Instant updatedAt, String summary) {
    public AgentSessionContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
        summary = summary == null ? "" : summary;
    }

    /**
     * 创建没有摘要的会话上下文。
     *
     * @param sessionId 会话标识
     * @param messages 会话消息
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AgentSessionContext(String sessionId, List<AgentMessage> messages,
                               Instant createdAt, Instant updatedAt) {
        this(sessionId, messages, createdAt, updatedAt, "");
    }
}
