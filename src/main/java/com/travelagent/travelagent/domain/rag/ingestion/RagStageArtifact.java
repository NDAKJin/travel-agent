package com.travelagent.travelagent.domain.rag.ingestion;

import com.travelagent.travelagent.domain.rag.model.ChunkMetadata;
import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.model.EmbeddingChunk;
import java.util.List;

/** Persisted hand-off between pipeline stages. */
public record RagStageArtifact(String fileName, String contentType, String bytesBase64,
                               String text, String mediaType, DocumentMetadata documentMetadata,
                               List<EmbeddingChunk> chunks, int writtenCount) {
}
