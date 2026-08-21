package com.travelagent.travelagent.rag.rerank;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "travel-agent.rag.rerank")
public class RerankProperties {
    private String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank";
    private String apiKey;
    private String model = "qwen3-rerank";
    private int topN = 5;
}
