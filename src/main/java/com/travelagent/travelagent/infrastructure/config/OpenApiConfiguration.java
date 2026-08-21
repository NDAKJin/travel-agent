package com.travelagent.travelagent.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI travelAgentOpenApi() {
        return new OpenAPI().info(new Info().title("旅行助手接口文档").version("v1"));
    }
}
