package com.travelagent.travelagent.agent.model;

import java.util.List;

public record SpecialistAgentResult(String agent, String status, String summary, Object data, List<String> warnings) {
}
