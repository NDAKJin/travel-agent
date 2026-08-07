package com.travelagent.travelagent.config;

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
                    "travel-agent.agent.prompt.override=custom prompt",
                    "travel-agent.agent.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "travel-agent.agent.qwen.api-key=test-key",
                    "travel-agent.agent.qwen.model=qwen-plus");

    @Test
    void bindsNestedAgentProperties() {
        contextRunner.run(context -> {
            AgentProperties properties = context.getBean(AgentProperties.class);
            assertThat(properties.getProfile().getName()).isEqualTo("Travel Buddy");
            assertThat(properties.getProfile().getDefaultLocale()).isEqualTo("zh-CN");
            assertThat(properties.getTool().isEnabled()).isFalse();
            assertThat(properties.getPrompt().getOverride()).isEqualTo("custom prompt");
            assertThat(properties.getQwen().getBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
            assertThat(properties.getQwen().getApiKey()).isEqualTo("test-key");
            assertThat(properties.getQwen().getModel()).isEqualTo("qwen-plus");
        });
    }

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfiguration {
    }
}
