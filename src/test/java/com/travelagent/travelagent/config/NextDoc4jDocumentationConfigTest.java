package com.travelagent.travelagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class NextDoc4jDocumentationConfigTest {

    @Test
    void applicationYmlEnablesNextDoc4jByDefault() {
        Properties properties = loadYaml("application.yml");
        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("nextdoc4j.enabled")).isEqualTo("true");
    }

    @Test
    void productionProfileDisablesDocumentationEndpoints() {
        Properties properties = loadYaml("application-prod.yml");
        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("nextdoc4j.enabled")).isEqualTo("false");
    }

    private Properties loadYaml(String location) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource(location));
        Properties properties = factoryBean.getObject();
        if (properties == null) {
            throw new IllegalStateException("Failed to load " + location);
        }
        return properties;
    }
}
