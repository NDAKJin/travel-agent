package com.travelagent.travelagent.rag.admin;

public record RagChunkResponse(long id, long documentId, String documentKey, String fileName,
                               int chunkIndex, int startOffset, int endOffset, String content,
                               String keywords, String summary, String questions, boolean enabled) { }
