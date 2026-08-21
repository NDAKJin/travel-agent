package com.travelagent.travelagent.config;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AgentBootstrapConfiguration {

    @Bean
    @Primary
    ChatClient finalizerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    BaseCheckpointSaver checkpointSaver(
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host:localhost}") String host,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.port:6379}") int port,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.username:}") String username,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.password:}") String password,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.database:0}") int database) {
        return RedisSaver.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .ttl(7, TimeUnit.DAYS)
                .build();
    }

    @Bean(name = "routeExpertExecutor")
    ThreadPoolTaskExecutor routeExpertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(3);
        executor.setThreadNamePrefix("route-expert-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("orchestrationChatClient")
    ChatClient orchestrationChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("routePlannerChatClient")
    ChatClient routePlannerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("normalServiceChatClient")
    ChatClient normalServiceChatClient(ChatModel chatModel) {
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

}
