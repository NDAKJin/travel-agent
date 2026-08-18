package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.service.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
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

    public String estimateBudget(String task) {
        return runner.run("budget", chatClient, promptResourceLoader.load("budget-agent"), task);
    }
}
