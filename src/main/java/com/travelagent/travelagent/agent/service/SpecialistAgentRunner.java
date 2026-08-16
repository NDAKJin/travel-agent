package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.observation.AgentObservationContext;
import com.travelagent.travelagent.agent.observation.AgentObservationContextHolder;
import java.time.Instant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAgentRunner {

    public String run(String agent, ChatClient chatClient, String prompt, String task) {
        AgentObservationContext observation = AgentObservationContextHolder.current();
        Instant startedAt = Instant.now();
        try {
            ChatResponse response = chatClient.prompt().system(prompt).user(task).call().chatResponse();
            String output = normalize(response.getResult().getOutput().getText());
            if (observation != null) observation.publish(agent, "llm", "success", startedAt,
                    "系统提示：\n" + prompt + "\n\n任务：\n" + task, output, response, null);
            return output;
        } catch (RuntimeException exception) {
            if (observation != null) observation.publish(agent, "llm", "error", startedAt,
                    "系统提示：\n" + prompt + "\n\n任务：\n" + task, null, null, exception);
            throw exception;
        }
    }

    static String normalize(String response) {
        return response == null ? "" : response.trim();
    }
}
