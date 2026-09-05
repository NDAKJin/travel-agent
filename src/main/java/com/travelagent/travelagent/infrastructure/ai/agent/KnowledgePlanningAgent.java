package com.travelagent.travelagent.infrastructure.ai.agent;

import com.travelagent.travelagent.domain.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationContextHolder;
import com.travelagent.travelagent.domain.rag.service.KnowledgeRagService;
import com.travelagent.travelagent.infrastructure.cache.RedisRagToolCache;
import java.time.Instant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePlanningAgent {

    private final KnowledgeRagService ragService;
    private final RedisRagToolCache cache;

    public KnowledgePlanningAgent(KnowledgeRagService ragService, RedisRagToolCache cache) {
        this.ragService = ragService;
        this.cache = cache;
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
            try {
                String cached = cache.get(task).orElse(null);
                if (cached != null) {
                    if (observation != null) {
                        observation.rememberToolResult("searchTravelKnowledge", task, cached);
                        observation.publish("knowledge", "tool", "cache_hit", startedAt, task, cached,
                                null, "return", null);
                    }
                    return cached;
                }
            } catch (RuntimeException ignored) {
                // Cache is an optimization; Redis outages must not break RAG.
            }
            String output = ragService.enrich(task);
            try {
                cache.put(task, output);
            } catch (RuntimeException ignored) {
                // Ignore cache write failures.
            }
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
