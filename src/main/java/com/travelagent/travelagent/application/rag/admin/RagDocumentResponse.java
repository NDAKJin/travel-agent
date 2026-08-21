package com.travelagent.travelagent.application.rag.admin;

import java.time.Instant;

public record RagDocumentResponse(long id, String documentKey, String fileName, String mediaType,
                                  String title, String author, String keywords, String summary,
                                  String questions, boolean enabled, int chunkCount, Instant createdAt, Instant updatedAt) { }
