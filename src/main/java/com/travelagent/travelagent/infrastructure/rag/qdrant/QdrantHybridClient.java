package com.travelagent.travelagent.infrastructure.rag.qdrant;

import com.travelagent.travelagent.domain.rag.service.LexicalSparseEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class QdrantHybridClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantHybridClient.class);
    private static final String COLLECTION_PATH = "/collections/{collection}";
    private static final String POINTS_PATH = "/collections/{collection}/points";
    private static final String DELETE_POINTS_PATH = "/collections/{collection}/points/delete";
    private static final String PAYLOAD_PATH = "/collections/{collection}/points/payload";
    private static final String IDF_MODIFIER = "idf";
    private static final String DENSE_VECTOR = "dense";
    private static final String SPARSE_VECTOR = "sparse";
    private static final String ENABLED_FIELD = "enabled";
    private final RestClient client;
    private final EmbeddingModel embeddingModel;
    private final LexicalSparseEncoder sparseEncoder;
    private final String collection;
    private final int recallTopK;
    private volatile boolean initialized;

    public QdrantHybridClient(RestClient.Builder builder, EmbeddingModel embeddingModel,
            LexicalSparseEncoder sparseEncoder,
            @Value("${spring.ai.vectorstore.qdrant.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.qdrant.port:6333}") int port,
            @Value("${travel-agent.rag.collection-name:travel_knowledge_hybrid}") String collection,
            @Value("${travel-agent.rag.recall-top-k:20}") int recallTopK,
            @Value("${spring.ai.vectorstore.qdrant.api-key:}") String apiKey) {
        RestClient.Builder configured = builder.baseUrl("http://" + host + ":" + port);
        if (apiKey != null && !apiKey.isBlank()) configured.defaultHeader("api-key", apiKey);
        this.client = configured.build();
        this.embeddingModel = embeddingModel;
        this.sparseEncoder = sparseEncoder;
        this.collection = collection;
        this.recallTopK = recallTopK;
    }

    public void upsert(String id, String text, Map<String, Object> payload) {
        List<Float> dense = floats(embeddingModel.embed(text));
        LexicalSparseEncoder.SparseVector sparse = sparseEncoder.encode(text);
        ensureCollection(dense.size());
        Map<String, Object> vector = Map.of(DENSE_VECTOR, dense,
                SPARSE_VECTOR, Map.of("indices", sparse.indices(), "values", sparse.values()));
        Map<String, Object> point = Map.of("id", id, "vector", vector, "payload", payload);
        upsertPoints(List.of(point));
    }

    public void upsert(List<Document> documents) {
        if (documents == null || documents.isEmpty()) return;
        List<Map<String, Object>> points = new ArrayList<>(documents.size());
        int dimensions = 0;
        for (Document document : documents) {
            List<Float> dense = floats(embeddingModel.embed(document.getText()));
            dimensions = dense.size();
            LexicalSparseEncoder.SparseVector sparse = sparseEncoder.encode(document.getText());
            Map<String, Object> vector = Map.of(DENSE_VECTOR, dense,
                    SPARSE_VECTOR, Map.of("indices", sparse.indices(), "values", sparse.values()));
            points.add(Map.of("id", document.getId(), "vector", vector, "payload", document.getMetadata()));
        }
        ensureCollection(dimensions);
        upsertPoints(points);
    }

    private void upsertPoints(List<Map<String, Object>> points) {
        client.put().uri(POINTS_PATH, collection).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points, "wait", true)).retrieve().toBodilessEntity();
    }

    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        client.post().uri(DELETE_POINTS_PATH, collection).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", ids, "wait", true)).retrieve().toBodilessEntity();
    }

    public void setEnabled(List<String> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) return;
        client.put().uri(PAYLOAD_PATH, collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("payload", Map.of("enabled", enabled), "points", ids, "wait", true))
                .retrieve().toBodilessEntity();
    }

    public List<Document> query(String text) {
        List<Float> dense = floats(embeddingModel.embed(text));
        LexicalSparseEncoder.SparseVector sparse = sparseEncoder.encode(text);
        ensureCollection(dense.size());
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> densePrefetch = Map.of("query", dense, "using", DENSE_VECTOR, "limit", recallTopK, "filter", enabledFilter());
        if (sparse.indices().length == 0) {
            body.put("prefetch", List.of(densePrefetch));
        } else {
            body.put("prefetch", List.of(densePrefetch,
                    Map.of("query", Map.of("indices", sparse.indices(), "values", sparse.values()), "using", SPARSE_VECTOR, "limit", recallTopK, "filter", enabledFilter())));
        }
        body.put("query", sparse.indices().length == 0 ? dense : Map.of("fusion", "rrf"));
        body.put("limit", recallTopK);
        body.put("with_payload", true);
        Map<String, Object> response = client.post().uri("/collections/{collection}/points/query", collection)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        if (response == null) return List.of();
        Object result = response.get("result");
        List<?> points;
        if (result instanceof List<?> list) {
            points = list;
        } else if (result instanceof Map<?, ?> resultMap && resultMap.get("points") instanceof List<?> list) {
            points = list;
        } else {
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        for (Object item : points) {
            if (!(item instanceof Map<?, ?> point)) continue;
            String id = String.valueOf(point.get("id"));
            String content = point.get("payload") instanceof Map<?, ?> p && p.get("content") != null
                    ? String.valueOf(p.get("content")) : "";
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (point.get("payload") instanceof Map<?, ?> p) {
                for (Map.Entry<?, ?> entry : p.entrySet()) {
                    metadata.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            Document.Builder builder = Document.builder().id(id).text(content).metadata(metadata);
            if (point.get("score") instanceof Number score) builder.score(score.doubleValue());
            documents.add(builder.build());
        }
        return documents;
    }

    private synchronized void ensureCollection(int dimensions) {
        if (initialized) return;
        try {
            client.get().uri(COLLECTION_PATH, collection).retrieve().toBodilessEntity();
            initialized = true;
            return;
        } catch (RuntimeException exception) {
            log.debug("Qdrant collection lookup failed; attempting creation: collection={}", collection, exception);
        }
        Map<String, Object> body = Map.of("vectors", Map.of("dense", Map.of("size", dimensions, "distance", "Cosine")),
                "sparse_vectors", Map.of(SPARSE_VECTOR, Map.of("modifier", IDF_MODIFIER)));
        client.put().uri(COLLECTION_PATH, collection).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
        initialized = true;
    }

    private List<Float> floats(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }

    private Map<String, Object> enabledFilter() {
        return Map.of("must", List.of(Map.of("key", ENABLED_FIELD, "match", Map.of("value", true))));
    }
}
