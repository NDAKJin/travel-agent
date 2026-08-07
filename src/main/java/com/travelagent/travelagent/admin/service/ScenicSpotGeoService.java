package com.travelagent.travelagent.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotRequest;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotResponse;
import com.travelagent.travelagent.config.AgentProperties;
import com.travelagent.travelagent.rag.service.ScenicKnowledgeIngestionService;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenicSpotGeoService {
    private final ObjectProvider<Rest5Client> restClientProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final ScenicKnowledgeIngestionService scenicKnowledgeIngestionService;

    public List<AdminScenicSpotResponse> list() {
        ensureIndex();
        JsonNode root = request("GET", "/" + indexName() + "/_search?size=1000", null);
        List<AdminScenicSpotResponse> result = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) result.add(toResponse(hit.path("_source")));
        return result;
    }

    public AdminScenicSpotResponse save(String id, AdminScenicSpotRequest input) {
        ensureIndex();
        String spotId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        Instant now = Instant.now();
        Map<String, Object> source = Map.of("id", spotId, "name", input.name().trim(),
                "description", input.description().trim(), "longitude", input.longitude(),
                "latitude", input.latitude(), "location", Map.of("lat", input.latitude(), "lon", input.longitude()),
                "updatedAt", now.toString());
        AdminScenicSpotResponse previous = findById(spotId);
        try {
            scenicKnowledgeIngestionService.ingestScenicSpot(spotId, input.name(), input.description());
            request("PUT", "/" + indexName() + "/_doc/" + spotId, source);
        } catch (RuntimeException exception) {
            if (previous == null) {
                scenicKnowledgeIngestionService.deleteDocument(spotId);
            } else {
                scenicKnowledgeIngestionService.ingestScenicSpot(spotId, previous.name(), previous.description());
            }
            throw exception;
        }
        return new AdminScenicSpotResponse(spotId, input.name().trim(), input.description().trim(),
                input.longitude(), input.latitude(), now);
    }

    public void delete(String id) {
        ensureIndex();
        AdminScenicSpotResponse previous = findById(id);
        try {
            scenicKnowledgeIngestionService.deleteDocument(id);
            request("DELETE", "/" + indexName() + "/_doc/" + id, null);
        } catch (RuntimeException exception) {
            if (previous != null) {
                scenicKnowledgeIngestionService.ingestScenicSpot(id, previous.name(), previous.description());
            }
            throw exception;
        }
    }

    public List<AdminScenicSpotResponse> nearby(double longitude, double latitude, String distance) {
        ensureIndex();
        Map<String, Object> body = Map.of(
                "size", 100,
                "sort", List.of(Map.of("_geo_distance", Map.of(
                        "location", Map.of("lat", latitude, "lon", longitude),
                        "order", "asc", "unit", "km"))),
                "query", Map.of("geo_distance", Map.of(
                        "distance", distance,
                        "location", Map.of("lat", latitude, "lon", longitude))));
        JsonNode root = request("POST", "/" + indexName() + "/_search", body);
        List<AdminScenicSpotResponse> result = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) result.add(toResponse(hit.path("_source")));
        return result;
    }

    public void ensureReadyForSearch() {
        ensureIndex();
    }

    private void ensureIndex() {
        String index = indexName();
        JsonNode mapping = request("GET", "/" + index + "/_mapping", null);
        JsonNode locationMapping = mapping.path(index).path("mappings").path("properties").path("location");
        String locationType = locationMapping.path("type").asText("");
        if (!locationMapping.isMissingNode() && !"geo_point".equals(locationType)) {
            List<JsonNode> documents = loadDocuments();
            request("DELETE", "/" + index, null);
            createIndex(index);
            for (JsonNode document : documents) {
                request("PUT", "/" + index + "/_doc/" + document.path("_id").asText(), document.path("_source"));
            }
            return;
        }
        if (locationMapping.isMissingNode()) {
            createIndex(index);
        }
    }

    private void createIndex(String index) {
        request("PUT", "/" + index, Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "keyword"),
                "name", Map.of("type", "text"),
                "description", Map.of("type", "text"),
                "longitude", Map.of("type", "double"),
                "latitude", Map.of("type", "double"),
                "location", Map.of("type", "geo_point"),
                "updatedAt", Map.of("type", "date")))));
    }

    private List<JsonNode> loadDocuments() {
        JsonNode root = request("GET", "/" + indexName() + "/_search?size=1000", null);
        List<JsonNode> documents = new ArrayList<>();
        root.path("hits").path("hits").forEach(documents::add);
        return documents;
    }

    private JsonNode request(String method, String endpoint, Object body) {
        Rest5Client restClient = restClientProvider.getIfAvailable();
        if (restClient == null) {
            throw new IllegalStateException("Elasticsearch client is not configured");
        }
        Request request = new Request(method, endpoint);
        try {
            if (body != null) request.setJsonEntity(objectMapper.writeValueAsString(body));
            Response response = restClient.performRequest(request);
            String payload = response.getEntity() == null ? "{}" : EntityUtils.toString(response.getEntity());
            if (response.getStatusCode() >= 400 && response.getStatusCode() != 404) {
                throw new IllegalStateException("Elasticsearch request failed: " + payload);
            }
            return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (IOException | ParseException exception) { throw new IllegalStateException("Elasticsearch scenic geo request failed", exception); }
    }

    private AdminScenicSpotResponse toResponse(JsonNode source) {
        return new AdminScenicSpotResponse(source.path("id").asText(), source.path("name").asText(),
                source.path("description").asText(), source.path("longitude").asDouble(),
                source.path("latitude").asDouble(), Instant.parse(source.path("updatedAt").asText()));
    }

    private AdminScenicSpotResponse findById(String id) {
        JsonNode root = request("GET", "/" + indexName() + "/_doc/" + id, null);
        JsonNode source = root.path("_source");
        return source.isMissingNode() || source.isEmpty() ? null : toResponse(source);
    }

    private String indexName() { return agentProperties.getRag().getElasticsearch().getIndexName() + "-geo"; }
}
