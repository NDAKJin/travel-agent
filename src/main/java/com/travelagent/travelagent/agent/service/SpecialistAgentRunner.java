package com.travelagent.travelagent.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAgentRunner {

    public String run(ChatClient chatClient, String prompt, String task) {
        return normalize(chatClient.prompt().system(prompt).user(task).call().content());
    }

    static String normalize(String response) {
        return response == null ? "" : response.trim();
    }
}
