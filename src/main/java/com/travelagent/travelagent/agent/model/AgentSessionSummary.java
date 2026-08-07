package com.travelagent.travelagent.agent.model;

import java.time.Instant;

public record AgentSessionSummary(String sessionId,
                                  String title,
                                  String preview,
                                  int messageCount,
                                  Instant updatedAt) {
}
