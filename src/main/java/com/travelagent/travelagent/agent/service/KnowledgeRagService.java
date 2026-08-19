package com.travelagent.travelagent.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeRagService {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public KnowledgeRagService(VectorStore vectorStore,
                                @Value("${travel-agent.rag.top-k:5}") int topK,
                                @Value("${travel-agent.rag.similarity-threshold:0.65}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public String enrich(String task) {
        if (!StringUtils.hasText(task)) return task;
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(task)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        JSONObject input = new JSONObject();
        input.put("task", parseOrText(task));
        input.put("knowledgeContext", documents.stream()
                .map(this::documentContext)
                .toList());
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private Map<String, Object> documentContext(Document document) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("content", document.getText());
        context.put("metadata", document.getMetadata());
        context.put("score", document.getScore());
        return context;
    }

    private Object parseOrText(String value) {
        try {
            return JSON.parse(value);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
