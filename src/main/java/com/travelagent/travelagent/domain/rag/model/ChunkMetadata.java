package com.travelagent.travelagent.domain.rag.model;

import java.util.List;

public record ChunkMetadata(List<String> keywords, String summary, List<String> questions) {
}
