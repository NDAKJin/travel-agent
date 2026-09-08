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
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RESULTS_FIELD = "results";
    private static final String OUTPUT_FIELD = "output";
    private static final String INDEX_FIELD = "index";
    private static final String SCORE_FIELD = "relevance_score";
    private static final int INVALID_INDEX = -1;

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
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + properties.getApiKey())
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .body(JSON.toJSONString(body))
                .retrieve()
                .body(String.class);
        return parseResults(raw, candidates);
    }

    private List<RerankResult> parseResults(String raw, List<RerankCandidate> candidates) {
        JSONObject response = JSON.parseObject(raw);
        JSONArray results = response == null ? null : response.getJSONArray(RESULTS_FIELD);
        if (results == null && response != null && response.getJSONObject(OUTPUT_FIELD) != null) {
            results = response.getJSONObject(OUTPUT_FIELD).getJSONArray(RESULTS_FIELD);
        }
        if (results == null) {
            throw new IllegalStateException("Qwen Rerank response does not contain results");
        }
        List<RerankResult> output = new ArrayList<>();
        for (int rank = 0; rank < results.size(); rank++) {
            JSONObject result = results.getJSONObject(rank);
            int index = result.getIntValue(INDEX_FIELD, INVALID_INDEX);
            Number scoreValue = result.get(SCORE_FIELD) instanceof Number number ? number : null;
            double score = scoreValue == null ? Double.NaN : scoreValue.doubleValue();
            if (index < 0 || index >= candidates.size() || Double.isNaN(score)) {
                throw new IllegalStateException("Qwen Rerank response contains invalid result");
            }
            output.add(new RerankResult(candidates.get(index).id(), score, rank + 1));
        }
        return output;
    }
}
