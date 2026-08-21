package com.travelagent.travelagent.rag.ingestion;

import java.time.Instant;

public record RagIngestionTaskResponse(Long id, String fileName, String status, int chunkCount, int writtenCount,
                                       String error, Instant createdAt, Instant updatedAt) { }
