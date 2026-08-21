package com.travelagent.travelagent.rag.ingestion;

import java.util.ArrayList;
import java.util.List;

final class RagIngestionContext {
    private final String fileName;
    private final String contentType;
    private final byte[] bytes;
    private String text;
    private String mediaType;
    private RagIngestionService.DocumentMetadata documentMetadata;
    private List<RagIngestionService.EmbeddingChunk> chunks = new ArrayList<>();
    private int writtenCount;

    RagIngestionContext(String fileName, String contentType, byte[] bytes) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    String fileName() { return fileName; }
    String contentType() { return contentType; }
    byte[] bytes() { return bytes; }
    String text() { return text; }
    void text(String text) { this.text = text; }
    String mediaType() { return mediaType; }
    void mediaType(String mediaType) { this.mediaType = mediaType; }
    RagIngestionService.DocumentMetadata documentMetadata() { return documentMetadata; }
    void documentMetadata(RagIngestionService.DocumentMetadata value) { this.documentMetadata = value; }
    List<RagIngestionService.EmbeddingChunk> chunks() { return chunks; }
    void chunks(List<RagIngestionService.EmbeddingChunk> value) { this.chunks = value; }
    int writtenCount() { return writtenCount; }
    void writtenCount(int value) { this.writtenCount = value; }
}
