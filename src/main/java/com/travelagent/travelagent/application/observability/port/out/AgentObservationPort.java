package com.travelagent.travelagent.application.observability.port.out;

import com.travelagent.travelagent.application.observability.model.AgentObservationEvent;

/** Agent 观测事件输出端口，Kafka 是当前实现。 */
public interface AgentObservationPort {

    void publish(AgentObservationEvent event);
}
