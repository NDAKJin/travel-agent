package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgentChatResponse", description = "Travel assistant chat response")
public record AgentChatResponse(
        @Schema(description = "Conversation session id", example = "session-1") String sessionId,
        @Schema(description = "Assistant reply", example = "Start with West Lake") String reply,
        @Schema(description = "Agent name", example = "Travel Buddy") String agentName,
        @Schema(description = "Model name", example = "qwen3.7-flash") String model,
        @Schema(description = "Whether tools are enabled", example = "false") boolean toolEnabled,
        NearbySearchResult nearbySearch,
        @Schema(description = "Whether the mini program should request location permission") boolean locationPermissionRequired) {
    public AgentChatResponse(String sessionId, String reply, String agentName, String model, boolean toolEnabled) {
        this(sessionId, reply, agentName, model, toolEnabled, null, false);
    }
}
