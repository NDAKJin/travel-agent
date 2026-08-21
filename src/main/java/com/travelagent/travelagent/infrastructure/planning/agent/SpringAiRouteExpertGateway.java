package com.travelagent.travelagent.infrastructure.planning.agent;

import com.travelagent.travelagent.infrastructure.ai.agent.BudgetAgent;
import com.travelagent.travelagent.infrastructure.ai.agent.KnowledgePlanningAgent;
import com.travelagent.travelagent.infrastructure.ai.agent.RoutePlanningAgent;
import com.travelagent.travelagent.application.planning.port.out.RouteExpertGateway;

/** 将 Spring AI 专家 Bean 适配为规划应用层端口。 */
public final class SpringAiRouteExpertGateway implements RouteExpertGateway {

    private final KnowledgePlanningAgent knowledgeAgent;
    private final RoutePlanningAgent routeAgent;
    private final BudgetAgent budgetAgent;

    public SpringAiRouteExpertGateway(KnowledgePlanningAgent knowledgeAgent,
                                      RoutePlanningAgent routeAgent,
                                      BudgetAgent budgetAgent) {
        this.knowledgeAgent = knowledgeAgent;
        this.routeAgent = routeAgent;
        this.budgetAgent = budgetAgent;
    }

    @Override
    public String execute(String expert, String task) {
        return switch (expert == null ? "" : expert.toUpperCase()) {
            case "KNOWLEDGE" -> require(knowledgeAgent).planKnowledge(task);
            case "ROUTE" -> require(routeAgent).planRoute(task);
            case "BUDGET" -> require(budgetAgent).estimateBudget(task);
            default -> throw new IllegalArgumentException("Unknown route expert: " + expert);
        };
    }

    private <T> T require(T expert) {
        if (expert == null) {
            throw new IllegalStateException("Route planning expert is unavailable");
        }
        return expert;
    }
}
