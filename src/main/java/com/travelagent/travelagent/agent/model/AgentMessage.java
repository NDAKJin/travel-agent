package com.travelagent.travelagent.agent.model;

import java.io.Serializable;

public record AgentMessage(String role,
                           String content) implements Serializable {
}
