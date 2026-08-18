package com.travelagent.travelagent.config;

import com.travelagent.travelagent.agent.subagent.BudgetAgent;
import com.travelagent.travelagent.agent.subagent.KnowledgePlanningAgent;
import com.travelagent.travelagent.agent.subagent.RoutePlanningAgent;
import javax.sql.DataSource;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    @Primary
    ChatClient finalizerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    BaseCheckpointSaver checkpointSaver(DataSource dataSource) {
        return MysqlSaver.builder()
                .dataSource(dataSource)
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
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
                                      BudgetAgent budgetAgent) {
        return specialistChatClient(chatModel, knowledgeAgent, routeAgent, budgetAgent);
    }

    @Bean("normalServiceChatClient")
    ChatClient normalServiceChatClient(ChatModel chatModel,
                                       KnowledgePlanningAgent knowledgeAgent,
                                       RoutePlanningAgent routeAgent,
                                       BudgetAgent budgetAgent) {
        return specialistChatClient(chatModel, knowledgeAgent, routeAgent, budgetAgent);
    }

    @Bean("knowledgePlanningChatClient")
    ChatClient knowledgePlanningChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("routePlanningChatClient")
    ChatClient routePlanningChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("budgetChatClient")
    ChatClient budgetChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient specialistChatClient(ChatModel chatModel,
                                            KnowledgePlanningAgent knowledgeAgent,
                                            RoutePlanningAgent routeAgent,
                                            BudgetAgent budgetAgent) {
        return ChatClient.builder(chatModel)
                .defaultTools(knowledgeAgent, routeAgent, budgetAgent)
                .build();
    }

}
