package com.travelagent.travelagent.domain.rag.model;

/** Message used to advance one RAG ingestion stage. */
public record RagStageMessage(long taskId, String stage, String artifactPath,
                              String fileName, String contentType) {
}
