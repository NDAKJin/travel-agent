package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgentConversationMessageResponse", description = "Stored conversation message")
public record AgentConversationMessageResponse(
        @Schema(description = "Message role", example = "user") String role,
        @Schema(description = "Message content", example = "Plan a two-day Hangzhou trip") String content) {
}
