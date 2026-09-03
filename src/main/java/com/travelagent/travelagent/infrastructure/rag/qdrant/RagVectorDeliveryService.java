package com.travelagent.travelagent.infrastructure.rag.qdrant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Mutable delivery telemetry kept separate from the append-only outbox. */
@Service
public class RagVectorDeliveryService {
    private final JdbcTemplate jdbc;

    public RagVectorDeliveryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensure(String eventId, String chunkKey, String operation) {
        jdbc.update("""
                INSERT INTO rag_vector_delivery (event_id, chunk_key, operation, canal_status, kafka_status,
                    qdrant_status, attempts, created_at, updated_at)
                VALUES (?, ?, ?, 'RECEIVED', 'PENDING', 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE chunk_key = VALUES(chunk_key), operation = VALUES(operation),
                    updated_at = CURRENT_TIMESTAMP
                """, parseId(eventId), chunkKey, operation);
    }

    public void markKafkaSent(String eventId) {
        jdbc.update("UPDATE rag_vector_delivery SET canal_status='ACKED', kafka_status='SUCCESS', updated_at=CURRENT_TIMESTAMP WHERE event_id=?",
                parseId(eventId));
    }

    public void markKafkaFailed(String eventId, String error) {
        jdbc.update("UPDATE rag_vector_delivery SET kafka_status='FAILED', last_error=?, updated_at=CURRENT_TIMESTAMP WHERE event_id=?",
                truncate(error), parseId(eventId));
    }

    public void markQdrantSuccess(String eventId) {
        jdbc.update("UPDATE rag_vector_delivery SET qdrant_status='SUCCESS', processed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE event_id=?",
                parseId(eventId));
    }

    public void markQdrantFailed(String eventId, String error) {
        jdbc.update("UPDATE rag_vector_delivery SET qdrant_status='FAILED', attempts=attempts+1, last_error=?, updated_at=CURRENT_TIMESTAMP WHERE event_id=?",
                truncate(error), parseId(eventId));
    }

    private long parseId(String eventId) {
        try { return Long.parseLong(eventId); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid vector event id: " + eventId, ex); }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
