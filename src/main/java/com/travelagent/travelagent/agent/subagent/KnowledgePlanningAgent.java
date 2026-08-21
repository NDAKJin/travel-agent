package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.observation.AgentObservationContextHolder;
import com.travelagent.travelagent.rag.KnowledgeRagService;
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
        Instant startedAt = Instant.now();
        try {
            String output = ragService.enrich(task);
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
