package com.travelagent.travelagent.rag.service;

import com.travelagent.travelagent.config.AgentProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenicKnowledgeIngestionService {

    private final ObjectProvider<VectorStore> scenicVectorStoreProvider;
    private final AgentProperties agentProperties;

    /** Publishes a document directly to the vector store; no local file is created. */
    public void publishDocument(String documentId, String content) {
        if (!StringUtils.hasText(documentId) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Knowledge document must not be empty");
        }
        VectorStore scenicVectorStore = vectorStore();
        if (!agentProperties.getRag().isEnabled() || scenicVectorStore == null) {
            throw new IllegalStateException("RAG indexing is not available");
        }

        try {
            scenicVectorStore.add(List.of(toDocument(documentId, content)));
            log.info("Scenic document published: file={}, contentLength={}", documentId, content.length());
        } catch (RuntimeException exception) {
            try {
                scenicVectorStore.delete(List.of(documentId));
            } catch (RuntimeException cleanup) {
                log.error("Failed to compensate scenic vector document: {}", documentId, cleanup);
            }
            throw new IllegalStateException("Failed to publish scenic knowledge document", exception);
        }
    }

    public void ingestScenicSpot(String id, String title, String description) {
        VectorStore scenicVectorStore = vectorStore();
        if (scenicVectorStore == null || !agentProperties.getRag().isEnabled()) return;
        scenicVectorStore.delete(List.of(id));
        String content = "# " + title.trim() + "\n\n" + description.trim();
        scenicVectorStore.add(List.of(new Document(id, content, java.util.Map.of(
                "title", title.trim(), "source", id, "type", "scenic-guide"))));
        log.info("Scenic spot indexed into RAG: id={}, titleLength={}, descriptionLength={}",
                id, title == null ? 0 : title.length(), description == null ? 0 : description.length());
    }

    public void deleteDocument(String id) {
        VectorStore scenicVectorStore = vectorStore();
        if (scenicVectorStore != null && agentProperties.getRag().isEnabled()) {
            scenicVectorStore.delete(List.of(id));
            log.info("Scenic document removed from RAG: id={}", id);
        }
    }

    private VectorStore vectorStore() {
        return scenicVectorStoreProvider.getIfAvailable();
    }

    private Document toDocument(String filename, String content) {
        String title = extractTitle(content, filename);
        return new Document(
                filename,
                stripFrontMatter(content).trim(),
                java.util.Map.of(
                        "title", title,
                        "source", filename,
                        "type", "scenic-guide"));
    }

    private String extractTitle(String content, String fallback) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return fallback;
    }

    private String stripFrontMatter(String content) {
        String[] lines = content.split("\\R");
        StringBuilder builder = new StringBuilder();
        boolean inFrontMatter = false;
        boolean frontMatterClosed = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!frontMatterClosed && trimmed.equals("---")) {
                inFrontMatter = !inFrontMatter;
                if (!inFrontMatter) {
                    frontMatterClosed = true;
                }
                continue;
            }
            if (inFrontMatter) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }
}
