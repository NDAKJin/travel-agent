package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.domain.rag.model.RagVectorSyncMessage;
import com.travelagent.travelagent.infrastructure.rag.qdrant.QdrantHybridClient;
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

    @KafkaListener(topics = "${travel-agent.rag.vector-topic:rag-vector-sync}",
            groupId = "${travel-agent.rag.vector-consumer-group:travel-agent-rag-vector-sync}")
    public void consume(String raw) {
        RagVectorSyncMessage message = JSON.parseObject(raw, RagVectorSyncMessage.class);
        if (message == null || message.chunkKey() == null || message.operation() == null) {
            throw new IllegalArgumentException("Invalid RAG vector sync message");
        }
        log.debug("Applying RAG vector event eventId={} operation={} chunkKey={}",
                message.eventId(), message.operation(), message.chunkKey());
        if ("DELETE".equalsIgnoreCase(message.operation())) {
            qdrant.delete(List.of(message.chunkKey()));
            return;
        }
        if (!"UPSERT".equalsIgnoreCase(message.operation())) {
            throw new IllegalArgumentException("Unsupported RAG vector operation: " + message.operation());
        }
        JSONObject payload = JSON.parseObject(message.payload());
        if (payload == null) throw new IllegalArgumentException("UPSERT payload is empty");
        Map<String, Object> metadata = payload.getJSONObject("metadata");
        qdrant.upsert(payload.getString("id", message.chunkKey()), payload.getString("text"), metadata);
    }
}
