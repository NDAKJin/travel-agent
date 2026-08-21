package com.travelagent.travelagent.application.rag.model;

public record RagIngestionMessage(long taskId, String path, String contentType, String fileName) {
}
