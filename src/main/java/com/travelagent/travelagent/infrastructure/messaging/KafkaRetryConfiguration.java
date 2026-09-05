package com.travelagent.travelagent.infrastructure.messaging;

import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskService;
import com.travelagent.travelagent.domain.rag.model.RagStageMessage;
import com.travelagent.travelagent.infrastructure.messaging.rag.CanalFlatMessageParser;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** Kafka container retry/backoff policy shared by all consumers. */
@Slf4j
@Configuration
public class KafkaRetryConfiguration {
    private final RagIngestionTaskService ingestionTasks;
    private final KafkaTemplate<String, String> kafka;
    private final String ingestionTopic;

    public KafkaRetryConfiguration(RagIngestionTaskService ingestionTasks, KafkaTemplate<String, String> kafka,
            @Value("${travel-agent.rag.ingestion-topic:rag-ingestion}") String ingestionTopic) {
        this.ingestionTasks = ingestionTasks;
        this.kafka = kafka;
        this.ingestionTopic = ingestionTopic;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0d);
        backOff.setMaxInterval(300_000L);
        backOff.setMaxElapsedTime(900_000L);
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(kafka);
        return new DefaultErrorHandler((record, exception) -> {
            recover(record, exception);
            dlt.accept(record, exception);
        }, backOff);
    }

    private void recover(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Kafka message exhausted retries: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        if (ingestionTopic.equals(record.topic()) && record.value() instanceof String raw) {
            try {
                if (raw.trim().startsWith("{\"taskId\"")) {
                    RagStageMessage stage = JSON.parseObject(raw, RagStageMessage.class);
                    if (stage != null) ingestionTasks.markFailed(stage.taskId(), exception.getMessage());
                    return;
                }
                List<JSONObject> rows = CanalFlatMessageParser.insertedRows(raw);
                for (JSONObject row : rows) {
                    String payload = row.getString("payload");
                    if (payload == null || payload.isBlank()) continue;
                    RagStageMessage stage = JSON.parseObject(payload, RagStageMessage.class);
                    if (stage != null && stage.stage() != null) ingestionTasks.markFailed(stage.taskId(), exception.getMessage());
                }
            } catch (RuntimeException parseError) {
                log.warn("Unable to mark failed RAG ingestion task from Kafka record", parseError);
            }
        }
    }
}
