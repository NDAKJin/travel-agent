package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AgentChatRequest", description = "Travel assistant chat request")
public record AgentChatRequest(
        @Schema(description = "User message", example = "Plan a two-day Hangzhou trip")
        @NotBlank(message = "message must not be blank")
        String message,
        @Schema(description = "Conversation session id", example = "session-1")
        String sessionId,
        @Schema(description = "Optional current location provided by the mini program")
        AgentUserLocation location) {
    public AgentChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }
}
