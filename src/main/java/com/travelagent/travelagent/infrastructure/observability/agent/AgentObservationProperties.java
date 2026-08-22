package com.travelagent.travelagent.infrastructure.observability.agent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "travel-agent.observability")
public class AgentObservationProperties {
    private String kafkaTopic = "agent-observation";
    private String consumerGroup = "travel-agent-observation-writer";
}
