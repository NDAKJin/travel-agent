package com.travelagent.travelagent.domain.agent.model;

/**
 * Agent 对话消息的角色枚举。
 */
public enum AgentMessageRole {
    USER("user"), ASSISTANT("assistant"), SYSTEM("system");

    private final String value;

    AgentMessageRole(String value) {
        this.value = value;
    }

    /**
     * 返回持久化和工作流协议使用的字符串值。
     *
     * @return 角色字符串值
     */
    public String value() {
        return value;
    }

    /**
     * 判断给定字符串是否对应当前角色。
     *
     * @param role 待判断的角色字符串
     * @return 如果匹配则返回 true，否则返回 false
     */
    public boolean matches(String role) {
        return value.equalsIgnoreCase(role);
    }
}
