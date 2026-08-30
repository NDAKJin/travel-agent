package com.travelagent.travelagent.infrastructure.ai.agent;

import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationContextHolder;
import com.travelagent.travelagent.domain.rag.service.KnowledgeRagService;
import java.time.Instant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePlanningAgent {

    private final KnowledgeRagService ragService;

    public KnowledgePlanningAgent(KnowledgeRagService ragService) {
        this.ragService = ragService;
    }

    @Tool(name = "searchTravelKnowledge", description = "检索旅行知识库，返回与任务最相关的景点、城市和路线知识")
    public String planKnowledge(String task) {
        AgentObservationContext observation = AgentObservationContextHolder.current();
        if (observation != null) {
            String cached = observation.toolResult("searchTravelKnowledge", task);
            if (cached != null) {
                return cached;
            }
        }
        Instant startedAt = Instant.now();
        try {
            String output = ragService.enrich(task);
            if (observation != null) {
                observation.rememberToolResult("searchTravelKnowledge", task, output);
            }
            if (observation != null) {
                observation.publish("knowledge", "tool", "success", startedAt, task, output,
                        null, "return", null);
            }
            return output;
        } catch (RuntimeException exception) {
            if (observation != null) {
                observation.publish("knowledge", "tool", "error", startedAt, task, null,
                        null, null, exception);
            }
            throw exception;
        }
    }
}
