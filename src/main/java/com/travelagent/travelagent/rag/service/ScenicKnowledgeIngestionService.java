package com.travelagent.travelagent.rag.service;

import com.travelagent.travelagent.config.AgentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public void ingestDocument(Path path) {
        long startedAt = System.nanoTime();
        VectorStore scenicVectorStore = vectorStore();
        if (scenicVectorStore == null || !agentProperties.getRag().isEnabled()) {
            log.warn("Scenic document ingestion skipped: path={}, enabled={}, vectorStoreAvailable={}",
                    path, agentProperties.getRag().isEnabled(), scenicVectorStore != null);
            return;
        }
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("Knowledge document does not exist");
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                throw new IllegalArgumentException("Knowledge document content must not be empty");
            }
            scenicVectorStore.add(List.of(toDocument(path.getFileName().toString(), content)));
            log.info("Scenic document ingested: file={}, contentLength={}, durationMs={}",
                    path.getFileName(), content.length(), elapsedMillis(startedAt));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read scenic knowledge document", exception);
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    public Path knowledgeDirectory() {
        return Paths.get(agentProperties.getRag().getKnowledgeDirectory()).toAbsolutePath().normalize();
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
