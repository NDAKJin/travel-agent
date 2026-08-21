package com.travelagent.travelagent.domain.agent.model;

import java.io.Serializable;

public record AgentMessage(String role,
                           String content) implements Serializable {
}
