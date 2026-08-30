package com.travelagent.travelagent.infrastructure.observability.agent;

import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.domain.observability.model.AgentObservationEvent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentObservationContextTest {

    @Test
    void publishesEventsWithTheBoundMessageId() {
        List<AgentObservationEvent> events = new ArrayList<>();
        AgentObservationContext context = new AgentObservationContext(42L, events::add);

        context.publish("supervisor", "before", "running", Instant.now(), null, null, null, null);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.messageId()).isEqualTo(42L);
            assertThat(event.sequenceNo()).isEqualTo(1);
        });
    }
}
