package com.travelagent.travelagent.agent.observation;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.mapper.AgentObservationLogMapper;
import com.travelagent.travelagent.agent.model.AgentObservationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "travel-agent.observability", name = "enabled", havingValue = "true")
@Slf4j
public class AgentObservationKafkaConsumer {
    private final AgentObservationLogMapper mapper;

    public AgentObservationKafkaConsumer(AgentObservationLogMapper mapper) {
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${travel-agent.observability.kafka-topic}",
            groupId = "${travel-agent.observability.consumer-group}")
    @Transactional
    public void consume(String payload) {
        AgentObservationEvent event = JSON.parseObject(payload, AgentObservationEvent.class);
        log.info("Received agent observation: eventId={}, messageId={}, sequenceNo={}",
                event.eventId(), event.messageId(), event.sequenceNo());
        AgentObservationLog observationLog = AgentObservationLog.builder()
                .eventId(event.eventId())
                .messageId(event.messageId())
                .traceId(event.traceId())
                .sequenceNo(event.sequenceNo())
                .agentName(event.agentName())
                .phase(event.phase())
                .status(event.status())
                .model(event.model())
                .llmInput(event.llmInput())
                .llmOutput(event.llmOutput())
                .promptTokens(event.promptTokens())
                .completionTokens(event.completionTokens())
                .totalTokens(event.totalTokens())
                .nextDecision(event.nextDecision())
                .durationMs(event.durationMs())
                .errorMessage(event.errorMessage())
                .createdAt(event.createdAt() == null ? java.time.Instant.now() : event.createdAt())
                .build();
        int inserted = mapper.insertIgnore(observationLog);
        log.info("Persisted agent observation: eventId={}, inserted={}", event.eventId(), inserted);
    }
}
