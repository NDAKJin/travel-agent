package com.travelagent.travelagent.config;

import com.travelagent.travelagent.agent.prompt.PromptProvider;
import com.travelagent.travelagent.agent.prompt.TravelAssistantPromptProvider;
import com.travelagent.travelagent.agent.tool.CurrentTimeTool;
import com.travelagent.travelagent.agent.tool.CurrentUserLocationTool;
import com.travelagent.travelagent.agent.tool.NearbySearchTool;
import com.travelagent.travelagent.agent.tool.TravelPlanningKnowledgeTool;
import com.travelagent.travelagent.agent.tool.LocationPermissionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    PromptProvider promptProvider(AgentProperties agentProperties,
                                  @Value("${travel-agent.neo4j.enabled:false}") boolean neo4jEnabled) {
        return new TravelAssistantPromptProvider(agentProperties, neo4jEnabled);
    }

    @Bean
    ChatClient reactAgentChatClient(ChatModel chatModel,
                                    CurrentTimeTool currentTimeTool,
                                    CurrentUserLocationTool currentUserLocationTool,
                                    LocationPermissionTool locationPermissionTool,
                                    NearbySearchTool nearbySearchTool,
                                    ObjectProvider<TravelPlanningKnowledgeTool> travelPlanningKnowledgeToolProvider) {
        var tools = new java.util.ArrayList<Object>();
        tools.add(currentTimeTool);
        tools.add(currentUserLocationTool);
        tools.add(locationPermissionTool);
        tools.add(nearbySearchTool);
        travelPlanningKnowledgeToolProvider.ifAvailable(tools::add);
        return ChatClient.builder(chatModel)
                .defaultTools(tools.toArray())
                .build();
    }
}
