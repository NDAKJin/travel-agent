package com.travelagent.travelagent.application.rag.ingestion;

import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.model.EmbeddingChunk;
import java.util.ArrayList;
import java.util.List;

final class RagIngestionContext {
    private final String fileName;
    private final String contentType;
    private final byte[] bytes;
    private String text;
    private String mediaType;
    private DocumentMetadata documentMetadata;
    private List<EmbeddingChunk> chunks = new ArrayList<>();
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
    DocumentMetadata documentMetadata() { return documentMetadata; }
    void documentMetadata(DocumentMetadata value) { this.documentMetadata = value; }
    List<EmbeddingChunk> chunks() { return chunks; }
    void chunks(List<EmbeddingChunk> value) { this.chunks = value; }
    int writtenCount() { return writtenCount; }
    void writtenCount(int value) { this.writtenCount = value; }
}
