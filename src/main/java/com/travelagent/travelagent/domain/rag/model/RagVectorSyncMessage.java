package com.travelagent.travelagent.domain.rag.model;

import java.time.Instant;

/** Event emitted from the MySQL vector outbox and delivered through Kafka. */
public record RagVectorSyncMessage(
        String eventId,
        String chunkKey,
        String operation,
        String payload,
        Instant createdAt) {
}
