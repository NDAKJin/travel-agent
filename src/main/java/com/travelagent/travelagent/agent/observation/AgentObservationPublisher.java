package com.travelagent.travelagent.agent.observation;

public interface AgentObservationPublisher {
    void publish(AgentObservationEvent event);
}
