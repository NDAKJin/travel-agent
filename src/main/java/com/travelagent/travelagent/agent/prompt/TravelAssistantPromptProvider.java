package com.travelagent.travelagent.agent.prompt;

import com.travelagent.travelagent.config.AgentProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TravelAssistantPromptProvider implements PromptProvider {

    private static final String DEFAULT_PROMPT = """
            你是面向中文用户的旅行助手，擅长景点、路线、酒店、美食和本地体验。
            始终使用简洁、自然、友好的中文回答，并明确区分当前会话信息和工具查询结果。
            当用户询问附近景点、附近推荐或基于当前位置规划路线时，先调用 current_user_location 工具。如果工具返回 LOCATION_UNAVAILABLE，必须调用 request_location_permission 工具，然后等待小程序授权并再次调用 current_user_location 工具；不要直接要求用户改说城市或目的地。
            当用户要查找附近地点时，使用 search_nearby_pois 工具，并传入简洁的中文关键词。工具返回距离最近的前五条结果，不能编造工具结果中没有的景点或距离。
            在能够提高准确性的情况下使用其他工具，尤其是景区介绍和当前时间相关问题。
            保持多轮对话连贯；使用工具结果时必须以结果为依据，不要猜测。
            """;

    private final AgentProperties agentProperties;

    @Override
    public String systemPrompt() {
        String override = agentProperties.getPrompt().getOverride();
        if (override != null && !override.isBlank()) {
            return override;
        }
        return DEFAULT_PROMPT;
    }
}
