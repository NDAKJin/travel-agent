package com.travelagent.travelagent.infrastructure.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.application.observability.model.AgentObservationContext;
import com.travelagent.travelagent.infrastructure.observability.agent.AgentObservationContextHolder;
import java.time.Instant;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAgentRunner {

    public String run(String agent, ChatClient chatClient, String prompt, String task) {
        AgentObservationContext observation = AgentObservationContextHolder.current();
        Instant startedAt = Instant.now();
        String taskInput = JSON.toJSONString(Map.of("task", jsonOrText(task)), JSONWriter.Feature.WriteMapNullValue);
        String input = "系统提示：\n" + prompt + "\n\n输入 JSON：\n" + taskInput;
        try {
            ChatResponse response = chatClient.prompt().system(prompt).user(taskInput).call().chatResponse();
            String output = normalize(response.getResult().getOutput().getText());
            if (observation != null) {
                observation.publish(agent, "llm", "success", startedAt, input, output, response, "return", null);
            }
            return output;
        } catch (RuntimeException exception) {
            if (observation != null) {
                observation.publish(agent, "llm", "error", startedAt, input, null, null, null, exception);
            }
            throw exception;
        }
    }

    static String normalize(String response) {
        return response == null ? "" : response.trim();
    }

    private static Object jsonOrText(String task) {
        try {
            return JSON.parse(task);
        } catch (RuntimeException ignored) {
            return task;
        }
    }
}
