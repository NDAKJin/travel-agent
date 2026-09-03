package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.domain.rag.model.RagIngestionMessage;
import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagIngestionKafkaConsumer {
    private final RagIngestionTaskService tasks;

    @KafkaListener(topics = "${travel-agent.rag.ingestion-topic:rag-ingestion}",
            groupId = "${travel-agent.rag.ingestion-consumer-group:travel-agent-rag-ingestion}")
    public void consume(String raw) {
        for (JSONObject row : CanalFlatMessageParser.insertedRows(raw)) {
            String payload = row.getString("payload");
            if (payload == null || payload.isBlank()) continue;
            RagIngestionMessage message = JSON.parseObject(payload, RagIngestionMessage.class);
            if (message == null) throw new IllegalArgumentException("Invalid RAG ingestion payload");
            log.info("RAG ingestion message received: taskId={}", message.taskId());
            tasks.process(message);
        }
    }
}
