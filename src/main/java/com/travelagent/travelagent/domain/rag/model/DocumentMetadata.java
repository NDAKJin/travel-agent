package com.travelagent.travelagent.domain.rag.model;

import java.util.List;

public record DocumentMetadata(String title, String author, List<String> keywords,
                               String summary, List<String> questions) {
}
