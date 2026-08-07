package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "AgentSessionDetailResponse", description = "Conversation session detail")
public record AgentSessionDetailResponse(
        @Schema(description = "Conversation session id", example = "session-1") String sessionId,
        @Schema(description = "Session title", example = "Hangzhou weekend trip") String title,
        @Schema(description = "Messages in the session") List<AgentConversationMessageResponse> messages,
        @Schema(description = "Created time", example = "2026-08-04T09:00:00Z") Instant createdAt,
        @Schema(description = "Last updated time", example = "2026-08-04T09:30:00Z") Instant updatedAt) {
}
