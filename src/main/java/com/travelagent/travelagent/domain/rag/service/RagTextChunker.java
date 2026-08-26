package com.travelagent.travelagent.domain.rag.service;

import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.model.EmbeddingChunk;
import java.util.ArrayList;
import java.util.List;

/** 纯文本分块规则，不依赖 Tika、LLM、数据库或向量库。 */
public final class RagTextChunker {

    private final int chunkSize;
    private final int overlap;
    private final List<String> separators;

    public RagTextChunker(int chunkSize, int overlap, List<String> separators) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunkSize/overlap is invalid");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.separators = List.copyOf(separators);
    }

    public List<EmbeddingChunk> split(String text, DocumentMetadata documentMetadata) {
        List<EmbeddingChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int maxEnd = Math.min(text.length(), start + chunkSize);
            int end = bestSplitPosition(text, start, maxEnd);
            if (end <= start || end - start <= overlap) end = maxEnd;
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new EmbeddingChunk(index++, start, end, content, documentMetadata, null));
            }
            if (end >= text.length()) break;
            int nextStart = end - overlap;
            start = nextStart > start ? nextStart : end;
        }
        return chunks;
    }

    private int bestSplitPosition(String text, int start, int maxEnd) {
        for (String separator : separators) {
            int position = text.lastIndexOf(separator, maxEnd - separator.length());
            if (position > start) return position + separator.length();
        }
        return maxEnd;
    }
}
