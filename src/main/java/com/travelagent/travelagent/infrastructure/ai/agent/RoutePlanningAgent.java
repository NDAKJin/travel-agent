package com.travelagent.travelagent.infrastructure.ai.agent;

import com.travelagent.travelagent.infrastructure.ai.prompt.PromptResourceLoader;
import com.travelagent.travelagent.infrastructure.ai.SpecialistAgentRunner;
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

    @Tool(name = "planTravelRoute", description = "根据给定的旅行任务、日期、地点和约束，规划可执行的旅行路线。只使用输入中提供或其他工具返回的事实，不要编造实时信息。")
    public String planRoute(String task) {
        return runner.run("route", chatClient, promptResourceLoader.load("route-agent"), task);
    }
}
