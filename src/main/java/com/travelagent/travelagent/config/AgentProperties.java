package com.travelagent.travelagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-agent.agent")
@Getter
public class AgentProperties {

    private final ProfileProperties profile = new ProfileProperties();
    private final ToolProperties tool = new ToolProperties();
    private final PromptProperties prompt = new PromptProperties();
    private final QwenProperties qwen = new QwenProperties();
    private final RagProperties rag = new RagProperties();

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
    public static class PromptProperties {

        private String override;

    }

    @Getter
    @Setter
    public static class QwenProperties {

        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey;
        private String model = "qwen-plus";

    }

    @Getter
    @Setter
    public static class RagProperties {

        private boolean enabled = true;
        private int topK = 4;
        private double similarityThreshold = 0.55d;
        private int maxContextChars = 4000;
        private int embeddingDimensions = 1024;
        private String knowledgeDirectory = "rag/scenic";
        private final ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

    }

    @Getter
    @Setter
    public static class ElasticsearchProperties {

        private String host = "localhost";
        private int port = 9200;
        private String scheme = "http";
        private String indexName = "travel-agent-scenic";
        private String geoIndexName = "travel-agent-geo";

    }
}
