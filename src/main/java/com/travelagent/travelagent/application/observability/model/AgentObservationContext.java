package com.travelagent.travelagent.application.observability.model;

import com.travelagent.travelagent.application.observability.port.out.AgentObservationPort;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public final class AgentObservationContext {
    private final long messageId;
    private final String traceId;
    private final AgentObservationPort publisher;
    private final AtomicInteger sequence = new AtomicInteger();

    public AgentObservationContext(long messageId, AgentObservationPort publisher) {
        this.messageId = messageId;
        this.traceId = UUID.randomUUID().toString();
        this.publisher = publisher;
    }

    public String traceId() { return traceId; }
    public static AgentObservationContext disabled() { return new AgentObservationContext(0L, event -> { }); }

    public void publish(String agentName, String phase, String status, Instant startedAt,
                        String llmInput, String llmOutput, ChatResponse response,
                        String nextDecision, Throwable error) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String model = response == null || response.getMetadata() == null ? null : response.getMetadata().getModel();
        int sequenceNo = sequence.incrementAndGet();
        publisher.publish(new AgentObservationEvent(UUID.randomUUID().toString(), messageId, traceId, sequenceNo,
                agentName, phase, status, model, llmInput, llmOutput,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(), nextDecision,
                startedAt == null ? null : java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                error == null ? null : error.getMessage(), Instant.now()));
    }

    public void publish(String agentName, String phase, String status, Instant startedAt,
                        String llmInput, String llmOutput, ChatResponse response, Throwable error) {
        publish(agentName, phase, status, startedAt, llmInput, llmOutput, response, null, error);
    }
}
