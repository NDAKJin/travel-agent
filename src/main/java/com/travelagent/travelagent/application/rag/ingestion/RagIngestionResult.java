package com.travelagent.travelagent.application.rag.ingestion;

public record RagIngestionResult(
        String fileName,
        String status,
        int chunkCount,
        int writtenCount,
        String error) {

    public static RagIngestionResult success(String fileName, int chunkCount, int writtenCount) {
        return new RagIngestionResult(fileName, "SUCCESS", chunkCount, writtenCount, null);
    }

    public static RagIngestionResult failure(String fileName, String error, int chunkCount, int writtenCount) {
        return new RagIngestionResult(fileName, "FAILED", chunkCount, writtenCount, error);
    }
}
