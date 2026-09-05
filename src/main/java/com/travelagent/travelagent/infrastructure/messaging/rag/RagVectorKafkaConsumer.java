package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.domain.rag.model.RagVectorSyncMessage;
import com.travelagent.travelagent.infrastructure.rag.qdrant.QdrantHybridClient;
import com.travelagent.travelagent.infrastructure.rag.qdrant.RagVectorDeliveryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Applies vector outbox events. Qdrant upsert/delete are idempotent by chunk key. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagVectorKafkaConsumer {
    private final QdrantHybridClient qdrant;
    private final RagVectorDeliveryService delivery;

    @KafkaListener(topics = "${travel-agent.rag.vector-topic:rag-vector-sync}",
            groupId = "${travel-agent.rag.vector-consumer-group:travel-agent-rag-vector-sync}")
    public void consume(String raw) {
        for (JSONObject row : CanalFlatMessageParser.insertedRows(raw)) {
            RagVectorSyncMessage message = toMessage(row);
            if (message == null) continue;
            process(message);
        }
    }

    private void process(RagVectorSyncMessage message) {
        delivery.ensure(message.eventId(), message.chunkKey(), message.operation());
        delivery.markKafkaSent(message.eventId());
        try {
            log.debug("Applying RAG vector event eventId={} operation={} chunkKey={}",
                    message.eventId(), message.operation(), message.chunkKey());
            if ("DELETE".equalsIgnoreCase(message.operation())) {
                qdrant.delete(List.of(message.chunkKey()));
            } else if ("DISABLE".equalsIgnoreCase(message.operation())) {
                qdrant.setEnabled(List.of(message.chunkKey()), false);
            } else {
                if (!"UPSERT".equalsIgnoreCase(message.operation())) {
                    throw new IllegalArgumentException("Unsupported RAG vector operation: " + message.operation());
                }
                JSONObject payload = JSON.parseObject(message.payload());
                if (payload == null) throw new IllegalArgumentException("UPSERT payload is empty");
                Map<String, Object> metadata = payload.getJSONObject("metadata");
                qdrant.upsert(payload.getString("id", message.chunkKey()), payload.getString("text"), metadata);
            }
            delivery.markQdrantSuccess(message.eventId());
        } catch (RuntimeException ex) {
            delivery.markQdrantFailed(message.eventId(), ex.getMessage());
            throw ex;
        }
    }

    private RagVectorSyncMessage toMessage(JSONObject row) {
        String eventId = row.getString("id");
        String chunkKey = row.getString("chunk_key");
        String operation = row.getString("operation");
        if (eventId == null || chunkKey == null || operation == null) return null;
        return new RagVectorSyncMessage(eventId, chunkKey, operation, row.getString("payload"),
                parseInstant(row.getString("created_at")));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        try {
            String normalized = value.replace(' ', 'T');
            if (!normalized.endsWith("Z")) normalized += "Z";
            return Instant.parse(normalized);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }
}
