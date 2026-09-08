package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;

/** Agent 会话列表中的轻量摘要值对象。 */
public record AgentSessionSummary(String sessionId,
                                  String title,
                                  String preview,
                                  int messageCount,
                                  Instant updatedAt) {
}
