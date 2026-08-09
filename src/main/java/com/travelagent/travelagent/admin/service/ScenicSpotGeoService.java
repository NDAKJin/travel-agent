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
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenicSpotGeoService {
    private static final String SCENIC_CATEGORY = "景区";

    private final ObjectProvider<Rest5Client> restClientProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final ScenicKnowledgeIngestionService scenicKnowledgeIngestionService;

    public List<AdminScenicSpotResponse> list() {
        long startedAt = System.nanoTime();
        JsonNode root = request("GET", "/" + indexName() + "/_search?size=1000", null);
        List<AdminScenicSpotResponse> result = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) result.add(toResponse(hit.path("_source"), false));
        log.info("Scenic spot list completed: resultCount={}, durationMs={}", result.size(), elapsedMillis(startedAt));
        return result;
    }

    public AdminScenicSpotResponse save(String id, AdminScenicSpotRequest input) {
        log.info("Saving scenic spot: idPresent={}, nameLength={}, descriptionLength={}",
                id != null && !id.isBlank(), input.name().length(), input.description().length());
        String spotId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        Instant now = Instant.now();
        Map<String, Object> source = Map.of("id", spotId, "name", input.name().trim(),
                "category", SCENIC_CATEGORY,
                "location", Map.of("lat", input.latitude(), "lon", input.longitude()),
                "updatedAt", now.toString());
        AdminScenicSpotResponse previous = findById(spotId);
        try {
            scenicKnowledgeIngestionService.ingestScenicSpot(spotId, input.name(), input.description());
            request("PUT", "/" + indexName() + "/_doc/" + spotId, source);
        } catch (RuntimeException exception) {
            if (previous == null) scenicKnowledgeIngestionService.deleteDocument(spotId);
            else scenicKnowledgeIngestionService.ingestScenicSpot(spotId, previous.name(), previous.description());
            throw exception;
        }
        return new AdminScenicSpotResponse(spotId, input.name().trim(), SCENIC_CATEGORY, input.description().trim(),
                input.longitude(), input.latitude(), now);
    }

    public AdminScenicSpotResponse get(String id) {
        AdminScenicSpotResponse spot = findById(id);
        if (spot == null) throw new IllegalArgumentException("Scenic spot not found");
        return spot;
    }

    public void delete(String id) {
        log.info("Deleting scenic spot: id={}", id);
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
        long startedAt = System.nanoTime();
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
        log.info("Nearby scenic spot list completed: resultCount={}, distance={}, durationMs={}",
                result.size(), distance, elapsedMillis(startedAt));
        return result;
    }

    public void ensureIndexReady() {
        String index = indexName();
        JsonNode mapping = request("GET", "/" + index + "/_mapping", null);
        if (mapping.path(index).isMissingNode()) createIndex(index);
    }

    private void createIndex(String index) {
        request("PUT", "/" + index, Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "keyword"),
                "name", Map.of("type", "text"),
                "category", Map.of("type", "keyword"),
                "location", Map.of("type", "geo_point"),
                "updatedAt", Map.of("type", "date")))));
    }

    private JsonNode request(String method, String endpoint, Object body) {
        Rest5Client restClient = restClientProvider.getIfAvailable();
        if (restClient == null) {
            throw new IllegalStateException("Elasticsearch client is not configured");
        }
        Request request = new Request(method, endpoint);
        long startedAt = System.nanoTime();
        try {
            if (body != null) request.setJsonEntity(objectMapper.writeValueAsString(body));
            Response response = restClient.performRequest(request);
            String payload = response.getEntity() == null ? "{}" : EntityUtils.toString(response.getEntity());
            log.debug("Scenic geo Elasticsearch request completed: method={}, endpoint={}, status={}, durationMs={}",
                    method, endpoint, response.getStatusCode(), elapsedMillis(startedAt));
            if (response.getStatusCode() >= 400 && response.getStatusCode() != 404) {
                log.error("Scenic geo Elasticsearch request returned an error: method={}, endpoint={}, status={}, durationMs={}",
                        method, endpoint, response.getStatusCode(), elapsedMillis(startedAt));
                throw new IllegalStateException("Elasticsearch request failed: " + payload);
            }
            return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (IOException | ParseException exception) {
            log.error("Scenic geo Elasticsearch request failed: method={}, endpoint={}, durationMs={}",
                    method, endpoint, elapsedMillis(startedAt), exception);
            throw new IllegalStateException("Elasticsearch scenic geo request failed", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private AdminScenicSpotResponse toResponse(JsonNode source) {
        return toResponse(source, true);
    }

    private AdminScenicSpotResponse toResponse(JsonNode source, boolean includeDescription) {
        String id = source.path("id").asText();
        String name = source.path("name").asText();
        String category = source.path("category").asText(SCENIC_CATEGORY);
        String description = includeDescription ? loadKnowledgeDescription(id, name) : "";
        JsonNode location = source.path("location");
        return new AdminScenicSpotResponse(id, name, category, description,
                location.path("lon").asDouble(), location.path("lat").asDouble(),
                Instant.parse(source.path("updatedAt").asText()));
    }

    private String loadKnowledgeDescription(String id, String name) {
        JsonNode byId = request("GET", "/" + knowledgeIndexName() + "/_doc/" + id, null).path("_source");
        String content = byId.path("content").asText("");
        if (!content.isBlank()) return removeTitleLine(content);

        JsonNode byTitle = request("POST", "/" + knowledgeIndexName() + "/_search", Map.of(
                "size", 1,
                "query", Map.of("match_phrase", Map.of("content", "# " + name)))).path("hits").path("hits");
        if (byTitle.isArray() && !byTitle.isEmpty()) {
            return removeTitleLine(byTitle.get(0).path("_source").path("content").asText(""));
        }
        return "";
    }

    private String removeTitleLine(String content) {
        String normalized = content.strip();
        if (normalized.startsWith("# ")) {
            int lineBreak = normalized.indexOf('\n');
            return lineBreak < 0 ? "" : normalized.substring(lineBreak + 1).strip();
        }
        return normalized;
    }

    private AdminScenicSpotResponse findById(String id) {
        JsonNode root = request("GET", "/" + indexName() + "/_doc/" + id, null);
        JsonNode source = root.path("_source");
        return source.isMissingNode() || source.isEmpty() ? null : toResponse(source);
    }

    private String indexName() { return agentProperties.getRag().getElasticsearch().getGeoIndexName(); }

    private String knowledgeIndexName() { return agentProperties.getRag().getElasticsearch().getIndexName(); }
}
