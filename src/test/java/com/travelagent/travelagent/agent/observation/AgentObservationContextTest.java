package com.travelagent.travelagent.agent.observation;

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
        context.close();

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.messageId()).isEqualTo(42L);
            assertThat(event.sequenceNo()).isEqualTo(1);
        });
    }
}
