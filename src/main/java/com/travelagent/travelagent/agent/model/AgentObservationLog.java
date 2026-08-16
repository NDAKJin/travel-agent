package com.travelagent.travelagent.agent.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
    private String nextDecision;
    private Long durationMs;
    private String errorMessage;
    private Instant createdAt;
}
