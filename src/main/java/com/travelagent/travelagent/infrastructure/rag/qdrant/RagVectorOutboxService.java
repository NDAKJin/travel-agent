package com.travelagent.travelagent.infrastructure.rag.qdrant;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.document.Document;

@Service
public class RagVectorOutboxService {
    private final JdbcTemplate jdbc;

    public RagVectorOutboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void enqueueUpsert(Document document) {
        JSONObject payload = new JSONObject();
        payload.put("id", document.getId()); payload.put("text", document.getText()); payload.put("metadata", document.getMetadata());
        enqueue(document.getId(), "UPSERT", payload.toJSONString());
    }

    @Transactional
    public void enqueueDelete(String chunkKey) { enqueue(chunkKey, "DELETE", null); }

    @Transactional
    public void enqueueDisable(String chunkKey) { enqueue(chunkKey, "DISABLE", null); }

    private void enqueue(String key, String operation, String payload) {
        jdbc.update("INSERT INTO rag_vector_outbox (chunk_key,operation,payload,created_at,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", key, operation, payload);
    }

}
