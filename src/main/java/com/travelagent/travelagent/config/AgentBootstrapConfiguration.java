package com.travelagent.travelagent.config;

import com.travelagent.travelagent.agent.prompt.PromptProvider;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.prompt.TravelAssistantPromptProvider;
import com.travelagent.travelagent.agent.subagent.BudgetAgent;
import com.travelagent.travelagent.agent.subagent.KnowledgePlanningAgent;
import com.travelagent.travelagent.agent.subagent.PoiSearchAgent;
import com.travelagent.travelagent.agent.subagent.RoutePlanningAgent;
import com.travelagent.travelagent.agent.tool.CurrentTimeTool;
import com.travelagent.travelagent.agent.tool.CurrentUserLocationTool;
import com.travelagent.travelagent.agent.tool.NearbySearchTool;
import com.travelagent.travelagent.agent.tool.TravelPlanningKnowledgeTool;
import com.travelagent.travelagent.agent.tool.LocationPermissionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    PromptProvider promptProvider(AgentProperties agentProperties, PromptResourceLoader promptResourceLoader) {
        return new TravelAssistantPromptProvider(agentProperties, promptResourceLoader);
    }

    @Bean
    @Primary
    ChatClient supervisorChatClient(ChatModel chatModel,
                                    CurrentTimeTool currentTimeTool,
                                    CurrentUserLocationTool currentUserLocationTool,
                                    LocationPermissionTool locationPermissionTool,
                                    ObjectProvider<KnowledgePlanningAgent> knowledgePlanningAgentProvider,
                                    ObjectProvider<RoutePlanningAgent> routePlanningAgentProvider,
                                    PoiSearchAgent poiSearchAgent,
                                    BudgetAgent budgetAgent) {
        var tools = new java.util.ArrayList<Object>();
        tools.add(currentTimeTool);
        tools.add(currentUserLocationTool);
        tools.add(locationPermissionTool);
        knowledgePlanningAgentProvider.ifAvailable(tools::add);
        routePlanningAgentProvider.ifAvailable(tools::add);
        tools.add(poiSearchAgent);
        tools.add(budgetAgent);
        return ChatClient.builder(chatModel)
                .defaultTools(tools.toArray())
                .build();
    }

    @Bean("knowledgePlanningChatClient")
    @ConditionalOnProperty(prefix = "travel-agent.neo4j", name = "enabled", havingValue = "true")
    ChatClient knowledgePlanningChatClient(ChatModel chatModel, TravelPlanningKnowledgeTool travelPlanningKnowledgeTool) {
        return ChatClient.builder(chatModel).defaultTools(travelPlanningKnowledgeTool).build();
    }

    @Bean("routePlanningChatClient")
    @ConditionalOnProperty(prefix = "travel-agent.neo4j", name = "enabled", havingValue = "true")
    ChatClient routePlanningChatClient(ChatModel chatModel, TravelPlanningKnowledgeTool travelPlanningKnowledgeTool) {
        return ChatClient.builder(chatModel).defaultTools(travelPlanningKnowledgeTool).build();
    }

    @Bean("poiSearchChatClient")
    ChatClient poiSearchChatClient(ChatModel chatModel, CurrentUserLocationTool currentUserLocationTool,
                                   LocationPermissionTool locationPermissionTool, NearbySearchTool nearbySearchTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(currentUserLocationTool, locationPermissionTool, nearbySearchTool)
                .build();
    }

    @Bean("budgetChatClient")
    ChatClient budgetChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
