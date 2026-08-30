package com.travelagent.travelagent.infrastructure.observability.agent;

import com.travelagent.travelagent.domain.observability.model.AgentObservationEvent;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
class AgentObservationConfiguration {

    @Bean
    AgentObservationPort kafkaAgentObservationPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                        AgentObservationProperties properties) {
        return new KafkaAgentObservationPublisher(kafkaTemplate, properties);
    }

    @Slf4j
    private static class KafkaAgentObservationPublisher implements AgentObservationPort {
        private final KafkaTemplate<String, String> kafkaTemplate;
        private final AgentObservationProperties properties;

        private KafkaAgentObservationPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                AgentObservationProperties properties) {
            this.kafkaTemplate = kafkaTemplate;
            this.properties = properties;
        }

        @Override
        public void publish(AgentObservationEvent event) {
            kafkaTemplate.send(properties.getKafkaTopic(), Long.toString(event.messageId()), JSON.toJSONString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish agent observation: eventId={}", event.eventId(), error);
                            return;
                        }
                        log.info("Published agent observation: eventId={}, messageId={}, topic={}, partition={}, offset={}",
                                event.eventId(), event.messageId(), result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    });
        }
    }
}
