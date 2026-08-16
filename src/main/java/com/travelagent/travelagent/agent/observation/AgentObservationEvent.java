package com.travelagent.travelagent.agent.observation;

import java.time.Instant;

public record AgentObservationEvent(
        String eventId,
        long messageId,
        String traceId,
        int sequenceNo,
        String agentName,
        String phase,
        String status,
        String model,
        String llmInput,
        String llmOutput,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs,
        String errorMessage,
        Instant createdAt) {
}
