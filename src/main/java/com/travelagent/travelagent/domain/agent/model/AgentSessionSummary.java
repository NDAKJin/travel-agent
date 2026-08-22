package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;

public record AgentSessionSummary(String sessionId,
                                  String title,
                                  String preview,
                                  int messageCount,
                                  Instant updatedAt) {
}
