package com.travelagent.travelagent.domain.rag.model;

import java.util.List;

public record EmbeddingChunk(int index, int startOffset, int endOffset, String content,
                             DocumentMetadata documentMetadata, ChunkMetadata chunkMetadata) {

    public EmbeddingChunk withChunkMetadata(ChunkMetadata metadata) {
        return new EmbeddingChunk(index, startOffset, endOffset, content, documentMetadata, metadata);
    }

    public String embeddingText() {
        return String.join("\n", List.of(
                empty(documentMetadata.title()),
                String.join("、", documentMetadata.keywords()),
                empty(documentMetadata.summary()),
                chunkMetadata == null ? "" : String.join("、", chunkMetadata.keywords()),
                chunkMetadata == null ? "" : empty(chunkMetadata.summary()),
                content));
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
