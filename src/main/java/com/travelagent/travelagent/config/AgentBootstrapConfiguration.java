package com.travelagent.travelagent.config;

import com.travelagent.travelagent.agent.prompt.PromptProvider;
import com.travelagent.travelagent.agent.prompt.TravelAssistantPromptProvider;
import com.travelagent.travelagent.agent.tool.CurrentTimeTool;
import com.travelagent.travelagent.agent.tool.ScenicIntroTool;
import com.travelagent.travelagent.agent.tool.CurrentUserLocationTool;
import com.travelagent.travelagent.agent.tool.NearbySearchTool;
import com.travelagent.travelagent.agent.tool.LocationPermissionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    PromptProvider promptProvider(AgentProperties agentProperties) {
        return new TravelAssistantPromptProvider(agentProperties);
    }

    @Bean
    ChatClient reactAgentChatClient(ChatModel chatModel,
                                    CurrentTimeTool currentTimeTool,
                                    ScenicIntroTool scenicIntroTool,
                                    CurrentUserLocationTool currentUserLocationTool,
                                    LocationPermissionTool locationPermissionTool,
                                    NearbySearchTool nearbySearchTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(currentTimeTool, scenicIntroTool, currentUserLocationTool, locationPermissionTool, nearbySearchTool)
                .defaultAdvisors(ToolCallingAdvisor.builder().build())
                .build();
    }
}
