package com.travelagent.travelagent.agent.observation;

import java.time.Instant;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public final class AgentObservationContext implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String METADATA_KEY = "agentObservation";
    private static final ConcurrentHashMap<String, AgentObservationPublisher> PUBLISHERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> SEQUENCES = new ConcurrentHashMap<>();

    private final long messageId;
    private final String traceId;

    public AgentObservationContext(long messageId, AgentObservationPublisher publisher) {
        this.messageId = messageId;
        this.traceId = UUID.randomUUID().toString();
        PUBLISHERS.put(traceId, publisher);
        SEQUENCES.put(traceId, new AtomicInteger());
    }

    public String traceId() {
        return traceId;
    }

    public static AgentObservationContext disabled() {
        return new AgentObservationContext(0L, event -> { });
    }

    public void close() {
        PUBLISHERS.remove(traceId);
        SEQUENCES.remove(traceId);
    }

    public void publish(String agentName, String phase, String status, Instant startedAt,
                        String llmInput, String llmOutput, ChatResponse response, Throwable error) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String model = response == null || response.getMetadata() == null ? null : response.getMetadata().getModel();
        AgentObservationPublisher publisher = PUBLISHERS.get(traceId);
        if (publisher == null) return;
        int sequenceNo = SEQUENCES.computeIfAbsent(traceId, ignored -> new AtomicInteger()).incrementAndGet();
        publisher.publish(new AgentObservationEvent(
                UUID.randomUUID().toString(), messageId, traceId, sequenceNo, agentName, phase, status,
                model, llmInput, llmOutput,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                startedAt == null ? null : java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                error == null ? null : error.getMessage(), Instant.now()));
    }
}
