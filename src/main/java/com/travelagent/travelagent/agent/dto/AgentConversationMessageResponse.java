package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgentConversationMessageResponse", description = "已保存的会话消息")
public record AgentConversationMessageResponse(
        @Schema(description = "消息角色", example = "user") String role,
        @Schema(description = "消息内容", example = "帮我规划杭州两日游") String content) {
}
