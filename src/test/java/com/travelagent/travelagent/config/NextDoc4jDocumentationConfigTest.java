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
        assertThat(properties.getProperty("nextdoc4j.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("nextdoc4j.auth.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("nextdoc4j.auth.password")).isEqualTo("123456");
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
