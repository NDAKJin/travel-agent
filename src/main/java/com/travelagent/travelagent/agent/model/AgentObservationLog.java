package com.travelagent.travelagent.agent.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentObservationLog {
    private Long id;
    private String eventId;
    private Long messageId;
    private String traceId;
    private int sequenceNo;
    private String agentName;
    private String phase;
    private String status;
    private String model;
    private String llmInput;
    private String llmOutput;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private String errorMessage;
    private Instant createdAt;
}
