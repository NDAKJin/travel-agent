package com.travelagent.travelagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-agent.agent")
@Getter
public class AgentProperties {

    private final ProfileProperties profile = new ProfileProperties();
    private final ToolProperties tool = new ToolProperties();
    private final ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

    @Setter
    private int maxMessageChars = 4000;
    @Setter
    private int maxSessionIdChars = 64;
    @Setter
    private int maxHistoryMessages = 40;

    @Getter
    @Setter
    public static class ProfileProperties {

        private String name = "Travel Buddy";
        private String defaultLocale = "zh-CN";

    }

    @Getter
    @Setter
    public static class ToolProperties {

        private boolean enabled;

    }

    @Getter
    @Setter
    public static class ElasticsearchProperties {

        private String host = "localhost";
        private int port = 9200;
        private String scheme = "http";
        private String geoIndexName = "travel-agent-geo";

    }
}
