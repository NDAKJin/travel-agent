package com.travelagent.travelagent.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.agent.model.SpecialistAgentResult;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAgentRunner {

    public String run(String agent, ChatClient chatClient, String prompt, String task) {
        String response = chatClient.prompt().system(prompt).user(task).call().content();
        return normalize(agent, response);
    }

    static String normalize(String agent, String response) {
        try {
            JSONObject result = JSON.parseObject(stripCodeFence(response));
            if (result == null || result.getString("summary") == null) throw new IllegalArgumentException("missing summary");
            List<String> warnings = result.getList("warnings", String.class);
            return JSON.toJSONString(new SpecialistAgentResult(agent,
                    normalizeStatus(result.getString("status")), result.getString("summary"),
                    result.get("data"), warnings == null ? List.of() : warnings));
        } catch (RuntimeException exception) {
            return JSON.toJSONString(new SpecialistAgentResult(agent, "partial", response, null,
                    List.of("子智能体未返回约定的 JSON 格式。")));
        }
    }

    private static String normalizeStatus(String status) {
        return List.of("success", "partial", "no_data", "error").contains(status) ? status : "success";
    }

    private static String stripCodeFence(String response) {
        String text = response == null ? "" : response.trim();
        return text.startsWith("```") ? text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "") : text;
    }
}
