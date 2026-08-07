package com.travelagent.travelagent.agent.model;

import java.time.Instant;
import java.util.List;

public record AgentSessionContext(String sessionId,
                                  List<AgentMessage> messages,
                                  Instant createdAt,
                                  Instant updatedAt) {
}
