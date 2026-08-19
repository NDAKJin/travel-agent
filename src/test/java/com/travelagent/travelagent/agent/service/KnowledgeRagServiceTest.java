package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeRagServiceTest {

    @Test
    void injectsRetrievedDocumentsIntoKnowledgeTask() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Nanjing Fuzi Temple")));

        String enriched = new KnowledgeRagService(vectorStore, 5, 0.65)
                .enrich("{\"destination\":\"Nanjing\"}");

        assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext")).hasSize(1);
        assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext").getJSONObject(0)
                .getString("content")).isEqualTo("Nanjing Fuzi Temple");
    }
}
