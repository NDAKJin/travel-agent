package com.travelagent.travelagent.config;

import com.travelagent.travelagent.agent.subagent.BudgetAgent;
import com.travelagent.travelagent.agent.subagent.KnowledgePlanningAgent;
import com.travelagent.travelagent.agent.subagent.PoiSearchAgent;
import com.travelagent.travelagent.agent.subagent.RoutePlanningAgent;
import com.travelagent.travelagent.agent.tool.CurrentTimeTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    @Primary
    ChatClient finalizerChatClient(ChatModel chatModel, CurrentTimeTool currentTimeTool) {
        return ChatClient.builder(chatModel)
                .defaultTools(currentTimeTool)
                .build();
    }

    @Bean("orchestrationChatClient")
    ChatClient orchestrationChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("routePlannerChatClient")
    ChatClient routePlannerChatClient(ChatModel chatModel,
                                      KnowledgePlanningAgent knowledgeAgent,
                                      RoutePlanningAgent routeAgent,
                                      PoiSearchAgent poiAgent,
                                      BudgetAgent budgetAgent) {
        return specialistChatClient(chatModel, knowledgeAgent, routeAgent, poiAgent, budgetAgent);
    }

    @Bean("normalServiceChatClient")
    ChatClient normalServiceChatClient(ChatModel chatModel,
                                       KnowledgePlanningAgent knowledgeAgent,
                                       RoutePlanningAgent routeAgent,
                                       PoiSearchAgent poiAgent,
                                       BudgetAgent budgetAgent) {
        return specialistChatClient(chatModel, knowledgeAgent, routeAgent, poiAgent, budgetAgent);
    }

    @Bean("knowledgePlanningChatClient")
    ChatClient knowledgePlanningChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("routePlanningChatClient")
    ChatClient routePlanningChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("poiSearchChatClient")
    ChatClient poiSearchChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("budgetChatClient")
    ChatClient budgetChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient specialistChatClient(ChatModel chatModel,
                                            KnowledgePlanningAgent knowledgeAgent,
                                            RoutePlanningAgent routeAgent,
                                            PoiSearchAgent poiAgent,
                                            BudgetAgent budgetAgent) {
        var tools = new java.util.ArrayList<Object>();
        tools.add(knowledgeAgent);
        tools.add(routeAgent);
        tools.add(poiAgent);
        tools.add(budgetAgent);
        return ChatClient.builder(chatModel).defaultTools(tools.toArray()).build();
    }
}
