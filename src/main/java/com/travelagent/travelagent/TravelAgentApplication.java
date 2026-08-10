package com.travelagent.travelagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(exclude = ElasticsearchVectorStoreAutoConfiguration.class)
@ConfigurationPropertiesScan
@MapperScan({
        "com.travelagent.travelagent.auth.mapper",
        "com.travelagent.travelagent.agent.mapper",
        "com.travelagent.travelagent.admin.mapper"
})
public class TravelAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelAgentApplication.class, args);
    }
}
