package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.domain.rag.model.RagStageMessage;
import com.travelagent.travelagent.domain.rag.ingestion.RagStageArtifact;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.kafka.core.KafkaTemplate;
import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagIngestionKafkaConsumer {
    private final RagIngestionTaskService tasks;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public RagIngestionKafkaConsumer(RagIngestionTaskService tasks, KafkaTemplate<String, String> kafka,
            @org.springframework.beans.factory.annotation.Value("${travel-agent.rag.ingestion-topic:rag-ingestion}") String topic) {
        this.tasks = tasks;
        this.kafka = kafka;
        this.topic = topic;
    }

    @KafkaListener(topics = "${travel-agent.rag.ingestion-topic:rag-ingestion}",
            groupId = "${travel-agent.rag.ingestion-consumer-group:travel-agent-rag-ingestion}")
    public void consume(String raw) {
        if (raw != null && raw.trim().startsWith("{\"taskId\"")) {
            RagStageMessage stage = JSON.parseObject(raw, RagStageMessage.class);
            if (stage != null && stage.stage() != null) { processStage(stage); return; }
        }
        for (JSONObject row : CanalFlatMessageParser.insertedRows(raw)) {
            String payload = row.getString("payload");
            if (payload == null || payload.isBlank()) continue;
            if (payload.trim().startsWith("{\"taskId\"")) {
                RagStageMessage stage = JSON.parseObject(payload, RagStageMessage.class);
                if (stage != null && stage.stage() != null) { processStage(stage); continue; }
            }
            throw new IllegalArgumentException("Invalid RAG stage payload");
        }
    }

    private void processStage(RagStageMessage message) {
        try {
            tasks.markStageRunning(message.taskId());
            RagStageArtifact input = JSON.parseObject(Files.readString(Path.of(message.artifactPath())), RagStageArtifact.class);
            RagStageArtifact output = tasks.ingestionService().processStage(message.stage(), input);
            tasks.markStageCompleted(message.taskId(), message.stage(), output);
            if ("PERSISTING".equals(message.stage())) return;
            String next = switch (message.stage()) {
                case "PARSING" -> "DOC_ENRICHING";
                case "DOC_ENRICHING" -> "CHUNKING";
                case "CHUNKING" -> "CHUNK_ENRICHING";
                case "CHUNK_ENRICHING" -> "PERSISTING";
                default -> throw new IllegalArgumentException("Unknown RAG stage: " + message.stage());
            };
            Path nextPath = Path.of(message.artifactPath()).getParent().resolve("stage-" + next + ".json");
            Files.writeString(nextPath, JSON.toJSONString(output));
            kafka.send(topic, Long.toString(message.taskId()), JSON.toJSONString(
                    new RagStageMessage(message.taskId(), next, nextPath.toString(), message.fileName(), message.contentType())));
        } catch (Exception e) {
            tasks.markFailed(message.taskId(), e.getMessage());
            throw new IllegalStateException("RAG stage failed: " + message.stage(), e);
        }
    }
}
