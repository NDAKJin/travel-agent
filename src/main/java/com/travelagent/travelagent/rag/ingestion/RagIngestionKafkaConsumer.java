package com.travelagent.travelagent.rag.ingestion;

import com.alibaba.fastjson2.JSON;
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
    public void consume(String payload) {
        RagIngestionTaskService.Message message = JSON.parseObject(payload, RagIngestionTaskService.Message.class);
        log.info("RAG ingestion message received: taskId={}", message.taskId());
        tasks.process(message);
    }
}
