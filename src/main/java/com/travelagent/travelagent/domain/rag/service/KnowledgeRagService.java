package com.travelagent.travelagent.domain.rag.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.rag.model.RerankCandidate;
import com.travelagent.travelagent.domain.rag.model.RerankResult;
import com.travelagent.travelagent.infrastructure.rag.rerank.QwenRerankService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.travelagent.travelagent.infrastructure.rag.qdrant.QdrantHybridClient;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class KnowledgeRagService {

    private final int topK;
    private final double similarityThreshold;
    private final int recallTopK;
    private final QwenRerankService rerankService;
    private final QdrantHybridClient hybridClient;

    @Autowired
    public KnowledgeRagService(
            @Value("${travel-agent.rag.top-k:5}") int topK,
            @Value("${travel-agent.rag.similarity-threshold:0.65}") double similarityThreshold,
            @Value("${travel-agent.rag.recall-top-k:20}") int recallTopK,
            QwenRerankService rerankService,
            QdrantHybridClient hybridClient) {
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.recallTopK = Math.max(topK, recallTopK);
        this.rerankService = rerankService;
        this.hybridClient = hybridClient;
    }

    public String enrich(String task) {
        if (!StringUtils.hasText(task))
            return task;
        List<Document> dense;
        dense = hybridClient.query(task);
        List<Document> documents = dense;
        List<RankedDocument> rerankedDocuments = rerank(task, documents);
        JSONObject input = new JSONObject();
        input.put("task", parseOrText(task));
        input.put("knowledgeContext", rerankedDocuments.stream()
                .map(this::documentContext)
                .toList());
        return JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue);
    }

    private List<RankedDocument> rerank(String query, List<Document> documents) {
        if (documents.isEmpty())
            return List.of();
        List<RerankCandidate> candidates = documents.stream()
                .map(document -> new RerankCandidate(document.getId(), document.getText()))
                .toList();
        Map<String, Document> documentsById = documents.stream()
                .collect(Collectors.toMap(Document::getId, Function.identity(), (first, ignored) -> first,
                        LinkedHashMap::new));
        return rerankService.rerank(query, candidates).stream()
                .limit(topK)
                .map(result -> new RankedDocument(documentsById.get(result.id()), result))
                .filter(ranked -> ranked.document() != null)
                .toList();
    }

    private Map<String, Object> documentContext(RankedDocument ranked) {
        Document document = ranked.document();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("content", document.getMetadata().getOrDefault("content", document.getText()));
        context.put("metadata", document.getMetadata());
        context.put("rerankScore", ranked.result().score());
        context.put("rerankRank", ranked.result().rank());
        context.put("vectorScore", document.getScore());
        return context;
    }

    private record RankedDocument(Document document, RerankResult result) {
    }

    private Object parseOrText(String value) {
        try {
            return JSON.parse(value);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
