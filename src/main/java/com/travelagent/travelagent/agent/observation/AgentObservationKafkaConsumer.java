package com.travelagent.travelagent.agent.observation;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.mapper.AgentObservationLogMapper;
import com.travelagent.travelagent.agent.model.AgentObservationLog;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "travel-agent.observability", name = "enabled", havingValue = "true")
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
        AgentObservationLog log = new AgentObservationLog();
        log.setEventId(event.eventId());
        log.setMessageId(event.messageId());
        log.setTraceId(event.traceId());
        log.setSequenceNo(event.sequenceNo());
        log.setAgentName(event.agentName());
        log.setPhase(event.phase());
        log.setStatus(event.status());
        log.setModel(event.model());
        log.setLlmInput(event.llmInput());
        log.setLlmOutput(event.llmOutput());
        log.setPromptTokens(event.promptTokens());
        log.setCompletionTokens(event.completionTokens());
        log.setTotalTokens(event.totalTokens());
        log.setDurationMs(event.durationMs());
        log.setErrorMessage(event.errorMessage());
        log.setCreatedAt(event.createdAt() == null ? Instant.now() : event.createdAt());
        mapper.insertIgnore(log);
    }
}
