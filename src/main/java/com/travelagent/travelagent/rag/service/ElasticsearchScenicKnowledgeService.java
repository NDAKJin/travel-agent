package com.travelagent.travelagent.rag.service;

import com.travelagent.travelagent.config.AgentProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchScenicKnowledgeService implements ScenicKnowledgeService {

    private final ObjectProvider<VectorStore> scenicVectorStoreProvider;
    private final AgentProperties agentProperties;

    @Override
    public String buildContext(String query) {
        long startedAt = System.nanoTime();
        if (!agentProperties.getRag().isEnabled() || !StringUtils.hasText(query)) {
            log.debug("RAG search skipped: enabled={}, queryPresent={}",
                    agentProperties.getRag().isEnabled(), StringUtils.hasText(query));
            return "";
        }
        VectorStore scenicVectorStore = scenicVectorStoreProvider.getIfAvailable();
        if (scenicVectorStore == null) {
            log.warn("RAG search skipped because scenic vector store is unavailable");
            return "";
        }
        try {
            List<Document> documents = scenicVectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(agentProperties.getRag().getTopK())
                    .similarityThreshold(agentProperties.getRag().getSimilarityThreshold())
                    .build());
            if (documents.isEmpty()) {
                log.info("RAG search completed: resultCount=0, durationMs={}", elapsedMillis(startedAt));
                return "";
            }
            String context = documents.stream()
                    .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(this::formatDocument)
                    .collect(Collectors.joining("\n\n"))
                    .strip();
            String limitedContext = context.substring(0, Math.min(context.length(), agentProperties.getRag().getMaxContextChars()));
            log.info("RAG search completed: resultCount={}, contextLength={}, durationMs={}",
                    documents.size(), limitedContext.length(), elapsedMillis(startedAt));
            return limitedContext;
        } catch (RuntimeException exception) {
            log.error("RAG search failed: queryLength={}, durationMs={}",
                    query.length(), elapsedMillis(startedAt), exception);
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String formatDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String title = metadata == null ? "scenic-knowledge" : String.valueOf(metadata.getOrDefault("title", "scenic-knowledge"));
        String source = metadata == null ? "" : String.valueOf(metadata.getOrDefault("source", ""));
        String content = document.getText();
        return "[" + title + "]" + (StringUtils.hasText(source) ? " (" + source + ")" : "") + "\n" + content;
    }
}
