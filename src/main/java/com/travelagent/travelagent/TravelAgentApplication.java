package com.travelagent.travelagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

// Spring AI Alibaba 2.0.0-M1.1 registers this nonexistent auto-configuration class.
@SpringBootApplication(excludeName = "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeMultimodalEmbeddingAutoConfiguration")
@ConfigurationPropertiesScan
@MapperScan({
        "com.travelagent.travelagent.infrastructure.persistence.auth",
        "com.travelagent.travelagent.infrastructure.persistence.agent"
})
public class TravelAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelAgentApplication.class, args);
    }
}
