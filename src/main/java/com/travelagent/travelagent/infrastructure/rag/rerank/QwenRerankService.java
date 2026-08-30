package com.travelagent.travelagent.infrastructure.rag.rerank;

import com.travelagent.travelagent.domain.rag.model.RerankCandidate;
import com.travelagent.travelagent.domain.rag.model.RerankResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class QwenRerankService {

    private final RerankProperties properties;
    private final RestClient restClient;

    public QwenRerankService(RerankProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String dashScopeApiKey,
            @Qualifier("externalRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        if (!StringUtils.hasText(properties.getApiKey())) {
            properties.setApiKey(dashScopeApiKey);
        }
    }

    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("Rerank query must not be blank");
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("DashScope API key is not configured");
        }

        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", candidates.stream().map(RerankCandidate::content).toList());
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", input,
                "parameters", Map.of("top_n", Math.min(properties.getTopN(), candidates.size())));

        String raw = restClient.post()
                .uri(properties.getEndpoint())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .body(JSON.toJSONString(body))
                .retrieve()
                .body(String.class);
        return parseResults(raw, candidates);
    }

    private List<RerankResult> parseResults(String raw, List<RerankCandidate> candidates) {
        JSONObject response = JSON.parseObject(raw);
        JSONArray results = response == null ? null : response.getJSONArray("results");
        if (results == null && response != null && response.getJSONObject("output") != null) {
            results = response.getJSONObject("output").getJSONArray("results");
        }
        if (results == null) {
            throw new IllegalStateException("Qwen Rerank response does not contain results");
        }
        List<RerankResult> output = new ArrayList<>();
        for (int rank = 0; rank < results.size(); rank++) {
            JSONObject result = results.getJSONObject(rank);
            int index = result.getIntValue("index", -1);
            Number scoreValue = result.get("relevance_score") instanceof Number number ? number : null;
            double score = scoreValue == null ? Double.NaN : scoreValue.doubleValue();
            if (index < 0 || index >= candidates.size() || Double.isNaN(score)) {
                throw new IllegalStateException("Qwen Rerank response contains invalid result");
            }
            output.add(new RerankResult(candidates.get(index).id(), score, rank + 1));
        }
        return output;
    }
}
