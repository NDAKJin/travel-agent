package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.service.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "travel-agent.neo4j", name = "enabled", havingValue = "true")
public class RoutePlanningAgent {

    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final SpecialistAgentRunner runner;

    public RoutePlanningAgent(@Qualifier("routePlanningChatClient") ChatClient chatClient,
                              PromptResourceLoader promptResourceLoader, SpecialistAgentRunner runner) {
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.runner = runner;
    }

    @Tool(name = "delegate_route_planning", description = "委派给路线规划专员，根据景点 ID 查询图谱连通关系和最短路径。")
    public String planRoute(String task) {
        return runner.run("route", chatClient, promptResourceLoader.load("route-agent"), task);
    }
}
