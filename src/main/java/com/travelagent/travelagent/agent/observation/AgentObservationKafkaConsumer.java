package com.travelagent.travelagent.agent.observation;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.mapper.AgentObservationLogMapper;
import com.travelagent.travelagent.agent.model.AgentObservationLog;
import java.time.Instant;
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
        AgentObservationLog observationLog = new AgentObservationLog();
        observationLog.setEventId(event.eventId());
        observationLog.setMessageId(event.messageId());
        observationLog.setTraceId(event.traceId());
        observationLog.setSequenceNo(event.sequenceNo());
        observationLog.setAgentName(event.agentName());
        observationLog.setPhase(event.phase());
        observationLog.setStatus(event.status());
        observationLog.setModel(event.model());
        observationLog.setLlmInput(event.llmInput());
        observationLog.setLlmOutput(event.llmOutput());
        observationLog.setPromptTokens(event.promptTokens());
        observationLog.setCompletionTokens(event.completionTokens());
        observationLog.setTotalTokens(event.totalTokens());
        observationLog.setNextDecision(event.nextDecision());
        observationLog.setDurationMs(event.durationMs());
        observationLog.setErrorMessage(event.errorMessage());
        observationLog.setCreatedAt(event.createdAt() == null ? Instant.now() : event.createdAt());
        int inserted = mapper.insertIgnore(observationLog);
        log.info("Persisted agent observation: eventId={}, inserted={}", event.eventId(), inserted);
    }
}
