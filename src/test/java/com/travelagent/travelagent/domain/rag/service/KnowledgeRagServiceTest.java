package com.travelagent.travelagent.domain.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.domain.rag.model.RerankResult;
import com.travelagent.travelagent.infrastructure.rag.rerank.QwenRerankService;
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

                QwenRerankService rerank = mock(QwenRerankService.class);
                when(rerank.rerank(any(), any())).thenAnswer(invocation -> {
                        List<?> candidates = invocation.getArgument(1);
                        return candidates.stream().map(candidate -> {
                                com.travelagent.travelagent.domain.rag.model.RerankCandidate c =
                                        (com.travelagent.travelagent.domain.rag.model.RerankCandidate) candidate;
                                return new RerankResult(c.id(), 0, candidates.indexOf(candidate) + 1);
                        }).toList();
                });
                String enriched = new KnowledgeRagService(vectorStore, 5, 0.65, 20, rerank)
                                .enrich("{\"destination\":\"Nanjing\"}");

                assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext")).hasSize(1);
                assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext").getJSONObject(0)
                                .getString("content")).isEqualTo("Nanjing Fuzi Temple");
        }

        @Test
        void injectsDocumentsInRerankOrder() {
                VectorStore vectorStore = mock(VectorStore.class);
                Document first = new Document("first");
                Document second = new Document("second");
                when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(first, second));
                QwenRerankService rerank = mock(QwenRerankService.class);
                when(rerank.rerank(any(), any())).thenReturn(List.of(
                                new RerankResult(second.getId(), 0.9, 1),
                                new RerankResult(first.getId(), 0.4, 2)));

                String enriched = new KnowledgeRagService(vectorStore, 1, 0.65, 2, rerank).enrich("鏃呰鐭ヨ瘑");

                assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext").getJSONObject(0)
                                .getString("content")).isEqualTo("second");
                assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext").getJSONObject(0)
                                .getDoubleValue("rerankScore")).isEqualTo(0.9);
                assertThat(JSON.parseObject(enriched).getJSONArray("knowledgeContext").getJSONObject(0)
                                .getIntValue("rerankRank")).isEqualTo(1);
        }
}
