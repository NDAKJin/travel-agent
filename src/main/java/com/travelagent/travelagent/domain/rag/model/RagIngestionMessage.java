package com.travelagent.travelagent.domain.rag.model;

public record RagIngestionMessage(long taskId, String path, String contentType, String fileName) {
}
