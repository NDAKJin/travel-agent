package com.travelagent.travelagent.infrastructure.config;

import com.travelagent.travelagent.application.auth.config.AuthProperties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "travel-agent.auth.issuer=travel-agent",
                    "travel-agent.auth.jwt-secret=test-secret",
                    "travel-agent.auth.access-token-ttl=PT20M",
                    "travel-agent.auth.refresh-token-ttl=P2D",
                    "travel-agent.auth.wx.app-id=wx-app-id",
                    "travel-agent.auth.wx.secret=wx-secret",
                    "travel-agent.auth.wx.code2-session-url=https://api.weixin.qq.com/sns/jscode2session");

    @Test
    void bindsNestedAuthProperties() {
        contextRunner.run(context -> {
            AuthProperties properties = context.getBean(AuthProperties.class);
            assertThat(properties.getIssuer()).isEqualTo("travel-agent");
            assertThat(properties.getJwtSecret()).isEqualTo("test-secret");
            assertThat(properties.getWx().getAppId()).isEqualTo("wx-app-id");
            assertThat(properties.getWx().getSecret()).isEqualTo("wx-secret");
            assertThat(properties.getWx().getCode2SessionUrl()).isEqualTo("https://api.weixin.qq.com/sns/jscode2session");
        });
    }

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfiguration {
    }
}
