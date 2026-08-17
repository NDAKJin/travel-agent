package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.service.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
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

    @Tool(name = "delegate_route_planning", description = "委派给路线规划专员，根据用户给出的起终点、时间和偏好设计合理的行程顺序。")
    public String planRoute(String task) {
        return runner.run("route", chatClient, promptResourceLoader.load("route-agent"), task);
    }
}
