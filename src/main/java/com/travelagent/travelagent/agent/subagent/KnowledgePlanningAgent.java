package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.service.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class KnowledgePlanningAgent {

    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final SpecialistAgentRunner runner;

    public KnowledgePlanningAgent(@Qualifier("knowledgePlanningChatClient") ChatClient chatClient,
                                  PromptResourceLoader promptResourceLoader, SpecialistAgentRunner runner) {
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.runner = runner;
    }

    @Tool(name = "delegate_travel_knowledge", description = "委派给旅行知识规划专员，基于用户提供的信息整理旅行主题、活动偏好和待确认事项。")
    public String planKnowledge(String task) {
        return runner.run("knowledge", chatClient, promptResourceLoader.load("knowledge-agent"), task);
    }
}
