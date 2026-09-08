package com.travelagent.travelagent.domain.agent.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Agent 工作流使用的对话消息值对象。
 */
public record AgentMessage(String role, String content) implements Serializable {
    public AgentMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * 使用消息角色枚举创建消息。
     *
     * @param role 消息角色
     * @param content 消息内容
     */
    public AgentMessage(AgentMessageRole role, String content) {
        this(Objects.requireNonNull(role, "role must not be null").value(), content);
    }
}
