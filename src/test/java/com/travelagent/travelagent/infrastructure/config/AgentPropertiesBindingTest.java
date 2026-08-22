package com.travelagent.travelagent.infrastructure.config;

import com.travelagent.travelagent.application.agent.config.AgentProperties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "travel-agent.agent.profile.name=Travel Buddy",
                    "travel-agent.agent.profile.default-locale=zh-CN",
                    "travel-agent.agent.tool.enabled=false",
                    "travel-agent.agent.max-message-chars=123",
                    "travel-agent.agent.max-session-id-chars=45",
                    "travel-agent.agent.max-history-messages=6");

    @Test
    void bindsNestedAgentProperties() {
        contextRunner.run(context -> {
            AgentProperties properties = context.getBean(AgentProperties.class);
            assertThat(properties.getProfile().getName()).isEqualTo("Travel Buddy");
            assertThat(properties.getProfile().getDefaultLocale()).isEqualTo("zh-CN");
            assertThat(properties.getTool().isEnabled()).isFalse();
            assertThat(properties.getMaxMessageChars()).isEqualTo(123);
            assertThat(properties.getMaxSessionIdChars()).isEqualTo(45);
            assertThat(properties.getMaxHistoryMessages()).isEqualTo(6);
        });
    }

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfiguration {
    }
}
