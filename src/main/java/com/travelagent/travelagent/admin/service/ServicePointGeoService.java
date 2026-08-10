package com.travelagent.travelagent.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.admin.dto.AdminServicePointRequest;
import com.travelagent.travelagent.admin.dto.AdminServicePointResponse;
import com.travelagent.travelagent.config.AgentProperties;
import com.travelagent.travelagent.common.dto.PageResponse;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServicePointGeoService {
    private static final String INDEX_CATEGORY = "便民服务";
    private final ObjectProvider<Rest5Client> restClientProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;

    public PageResponse<AdminServicePointResponse> list(String category, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        Map<String, Object> query = new HashMap<>();
        query.put("filter", List.of(Map.of("term", Map.of("categoryGroup", INDEX_CATEGORY))));
        if (StringUtils.hasText(category)) {
            query.put("must", List.of(Map.of("term", Map.of("category", category.trim()))));
        }
        JsonNode root = request("POST", "/" + indexName() + "/_search", Map.of(
                "from", offset, "size", safeSize, "track_total_hits", true,
                "query", Map.of("bool", query),
                "sort", List.of(Map.of("updatedAt", Map.of("order", "desc")))));
        List<AdminServicePointResponse> result = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) result.add(toResponse(hit.path("_source")));
        JsonNode totalNode = root.path("hits").path("total");
        long total = totalNode.isNumber() ? totalNode.asLong() : totalNode.path("value").asLong();
        return new PageResponse<>(result, total, safePage, safeSize);
    }

    public AdminServicePointResponse get(String id) {
        JsonNode source = request("GET", "/" + indexName() + "/_doc/" + id, null).path("_source");
        if (source.isMissingNode() || !INDEX_CATEGORY.equals(source.path("categoryGroup").asText()))
            throw new IllegalArgumentException("便民服务点不存在");
        return toResponse(source);
    }

    public AdminServicePointResponse save(String id, AdminServicePointRequest input) {
        String pointId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        Instant now = Instant.now();
        Map<String, Object> source = Map.of("id", pointId, "name", input.name().trim(),
                "category", input.category().trim(), "categoryGroup", INDEX_CATEGORY,
                "description", input.description().trim(), "address", input.address() == null ? "" : input.address().trim(),
                "longitude", input.longitude(), "latitude", input.latitude(),
                "location", Map.of("lat", input.latitude(), "lon", input.longitude()), "updatedAt", now.toString());
        request("PUT", "/" + indexName() + "/_doc/" + pointId, source);
        return new AdminServicePointResponse(pointId, input.name().trim(), input.category().trim(),
                input.description().trim(), input.address() == null ? "" : input.address().trim(),
                input.longitude(), input.latitude(), now);
    }

    public void delete(String id) {
        get(id);
        request("DELETE", "/" + indexName() + "/_doc/" + id, null);
    }

    private AdminServicePointResponse toResponse(JsonNode source) {
        JsonNode location = source.path("location");
        double longitude = source.has("longitude") ? source.path("longitude").asDouble() : location.path("lon").asDouble();
        double latitude = source.has("latitude") ? source.path("latitude").asDouble() : location.path("lat").asDouble();
        return new AdminServicePointResponse(source.path("id").asText(), source.path("name").asText(),
                source.path("category").asText(), source.path("description").asText(""), source.path("address").asText(""),
                longitude, latitude, Instant.parse(source.path("updatedAt").asText()));
    }

    private JsonNode request(String method, String endpoint, Object body) {
        Rest5Client client = restClientProvider.getIfAvailable();
        if (client == null) throw new IllegalStateException("Elasticsearch client is not configured");
        try {
            Request request = new Request(method, endpoint);
            if (body != null) request.setJsonEntity(objectMapper.writeValueAsString(body));
            var response = client.performRequest(request);
            String payload = response.getEntity() == null ? "{}" : EntityUtils.toString(response.getEntity());
            if (response.getStatusCode() >= 400 && response.getStatusCode() != 404)
                throw new IllegalStateException("Elasticsearch request failed: " + payload);
            return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("便民服务地理数据请求失败", e);
        }
    }

    private String indexName() { return agentProperties.getRag().getElasticsearch().getGeoIndexName(); }
}
