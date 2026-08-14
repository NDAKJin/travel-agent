package com.travelagent.travelagent.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAgentRunner {

    public String run(ChatClient chatClient, String prompt, String task) {
        return chatClient.prompt().system(prompt).user(task).call().content();
    }
}
