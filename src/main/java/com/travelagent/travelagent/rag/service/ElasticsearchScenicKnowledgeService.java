package com.travelagent.travelagent.rag.service;

import com.travelagent.travelagent.config.AgentProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ElasticsearchScenicKnowledgeService implements ScenicKnowledgeService {

    private final ObjectProvider<VectorStore> scenicVectorStoreProvider;
    private final AgentProperties agentProperties;

    @Override
    public String buildContext(String query) {
        if (!agentProperties.getRag().isEnabled() || !StringUtils.hasText(query)) {
            return "";
        }
        VectorStore scenicVectorStore = scenicVectorStoreProvider.getIfAvailable();
        if (scenicVectorStore == null) {
            return "";
        }
        List<Document> documents = scenicVectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(agentProperties.getRag().getTopK())
                .similarityThreshold(agentProperties.getRag().getSimilarityThreshold())
                .build());
        if (documents.isEmpty()) {
            return "";
        }
        String context = documents.stream()
                .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::formatDocument)
                .collect(Collectors.joining("\n\n"))
                .strip();
        return context.substring(0, Math.min(context.length(), agentProperties.getRag().getMaxContextChars()));
    }

    private String formatDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String title = metadata == null ? "scenic-knowledge" : String.valueOf(metadata.getOrDefault("title", "scenic-knowledge"));
        String source = metadata == null ? "" : String.valueOf(metadata.getOrDefault("source", ""));
        String content = document.getText();
        return "[" + title + "]" + (StringUtils.hasText(source) ? " (" + source + ")" : "") + "\n" + content;
    }
}
