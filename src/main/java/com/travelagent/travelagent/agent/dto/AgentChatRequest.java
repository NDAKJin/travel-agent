package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AgentChatRequest", description = "旅行助手对话请求")
public record AgentChatRequest(
        @Schema(description = "用户消息", example = "帮我规划杭州两日游")
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,
        @Schema(description = "会话标识；为空时创建新会话", example = "session-1")
        @Size(max = 64, message = "sessionId must not exceed 64 characters") String sessionId,
        @Schema(description = "小程序提供的可选当前位置")
        AgentUserLocation location) {
    public AgentChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }
}
