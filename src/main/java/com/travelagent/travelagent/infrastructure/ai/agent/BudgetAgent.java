package com.travelagent.travelagent.infrastructure.ai.agent;

import com.travelagent.travelagent.infrastructure.ai.prompt.PromptResourceLoader;
import com.travelagent.travelagent.infrastructure.ai.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BudgetAgent {

    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final SpecialistAgentRunner runner;

    public BudgetAgent(@Qualifier("budgetChatClient") ChatClient chatClient,
                       PromptResourceLoader promptResourceLoader, SpecialistAgentRunner runner) {
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.runner = runner;
    }

    @Tool(name = "estimateTravelBudget", description = "根据旅行需求和路线信息估算预算，区分已知费用、未知费用和估算依据。不要编造实时价格。")
    public String estimateBudget(String task) {
        return runner.run("budget", chatClient, promptResourceLoader.load("budget-agent"), task);
    }
}
