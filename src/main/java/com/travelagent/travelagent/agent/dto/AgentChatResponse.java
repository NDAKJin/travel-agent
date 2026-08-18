package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgentChatResponse", description = "旅行助手对话响应")
public record AgentChatResponse(
        @Schema(description = "会话标识", example = "session-1") String sessionId,
        @Schema(description = "助手回复", example = "第一天可游览西湖和灵隐寺。") String reply,
        @Schema(description = "助手名称", example = "Travel Buddy") String agentName,
        @Schema(description = "本次调用的模型名称", example = "qwen3.8-max") String model,
        @Schema(description = "是否启用工具调用", example = "true") boolean toolEnabled) { }
