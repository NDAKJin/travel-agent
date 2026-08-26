package com.travelagent.travelagent.infrastructure.config;

import com.travelagent.travelagent.infrastructure.ai.agent.KnowledgePlanningAgent;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
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
    ThreadPoolTaskExecutor routeExpertExecutor(
            @Value("${travel-agent.route-expert.core-pool-size:8}") int corePoolSize,
            @Value("${travel-agent.route-expert.max-pool-size:16}") int maxPoolSize,
            @Value("${travel-agent.route-expert.queue-capacity:32}") int queueCapacity,
            @Value("${travel-agent.route-expert.keep-alive-seconds:60}") int keepAliveSeconds,
            @Value("${travel-agent.route-expert.allow-core-thread-timeout:false}") boolean allowCoreThreadTimeout,
            @Value("${travel-agent.route-expert.thread-name-prefix:route-expert-}") String threadNamePrefix,
            @Value("${travel-agent.route-expert.wait-for-tasks-to-complete-on-shutdown:true}") boolean waitForTasks,
            @Value("${travel-agent.route-expert.await-termination-seconds:30}") int awaitTerminationSeconds,
            @Value("${travel-agent.route-expert.rejection-policy:caller-runs}") String rejectionPolicy) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeout);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasks);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setRejectedExecutionHandler(rejectionHandler(rejectionPolicy));
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectionHandler(String policy) {
        return switch (policy.trim().toLowerCase(Locale.ROOT)) {
            case "caller-runs" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discard-oldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case "abort" -> new ThreadPoolExecutor.AbortPolicy();
            default -> throw new IllegalArgumentException("Unsupported route expert rejection policy: " + policy);
        };
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
    ChatClient normalServiceChatClient(ChatModel chatModel, KnowledgePlanningAgent knowledgeAgent) {
        return ChatClient.builder(chatModel).defaultTools(knowledgeAgent).build();
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
