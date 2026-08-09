package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AgentChatRequest", description = "Travel assistant chat request")
public record AgentChatRequest(
        @Schema(description = "User message", example = "Plan a two-day Hangzhou trip")
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,
        @Schema(description = "Conversation session id", example = "session-1")
        @Size(max = 64, message = "sessionId must not exceed 64 characters") String sessionId,
        @Schema(description = "Optional current location provided by the mini program")
        AgentUserLocation location) {
    public AgentChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }
}
