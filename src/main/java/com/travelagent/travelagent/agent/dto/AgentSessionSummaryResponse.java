package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "AgentSessionSummaryResponse", description = "Conversation session summary")
public record AgentSessionSummaryResponse(
        @Schema(description = "Conversation session id", example = "session-1") String sessionId,
        @Schema(description = "Session title", example = "Hangzhou weekend trip") String title,
        @Schema(description = "Last message preview", example = "Then visit Lingyin Temple in the afternoon.") String preview,
        @Schema(description = "Stored message count", example = "6") int messageCount,
        @Schema(description = "Last updated time", example = "2026-08-04T09:30:00Z") Instant updatedAt) {
}
