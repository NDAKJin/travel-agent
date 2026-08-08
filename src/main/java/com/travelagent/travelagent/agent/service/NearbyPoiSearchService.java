package com.travelagent.travelagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.agent.dto.NearbyPoi;
import com.travelagent.travelagent.agent.dto.NearbySearchResult;
import com.travelagent.travelagent.config.AgentProperties;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NearbyPoiSearchService {
    private static final int PAGE_SIZE = 5;
    private static final int QUERY_SIZE = PAGE_SIZE + 1;

    private final ObjectProvider<Rest5Client> restClientProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;

    public NearbySearchResult search(double latitude, double longitude, String keyword,
                                     int radiusMeters, List<Object> searchAfter) {
        long startedAt = System.nanoTime();
        int safeRadius = Math.max(100, Math.min(radiusMeters, 100_000));
        log.info("Nearby POI search started: latitude={}, longitude={}, keywordLength={}, radiusMeters={}, paged={}",
                latitude, longitude, keyword == null ? 0 : keyword.length(), safeRadius,
                searchAfter != null && !searchAfter.isEmpty());
        Map<String, Object> location = Map.of("lat", latitude, "lon", longitude);
        Map<String, Object> geoDistance = Map.of("distance", safeRadius + "m", "location", location);
        Map<String, Object> bool = new HashMap<>();
        bool.put("filter", List.of(Map.of("geo_distance", geoDistance)));
        if (StringUtils.hasText(keyword)) {
            bool.put("must", List.of(Map.of("multi_match", Map.of(
                    "query", keyword.trim(), "fields", List.of("name", "description")))));
        } else {
            bool.put("must", List.of(Map.of("match_all", Map.of())));
        }

        Map<String, Object> geoSort = new HashMap<>();
        geoSort.put("_geo_distance", Map.of("location", location, "order", "asc", "unit", "m"));
        Map<String, Object> body = new HashMap<>();
        body.put("size", QUERY_SIZE);
        body.put("query", Map.of("bool", bool));
        body.put("sort", List.of(geoSort, Map.of("id", "asc")));
        if (searchAfter != null && !searchAfter.isEmpty()) body.put("search_after", searchAfter);

        JsonNode root = request("POST", "/" + indexName() + "/_search", body);
        List<NearbyPoi> pois = new ArrayList<>();
        List<Object> nextCursor = List.of();
        boolean hasMore = false;
        for (JsonNode hit : root.path("hits").path("hits")) {
            if (pois.size() >= PAGE_SIZE) {
                hasMore = true;
                break;
            }
            JsonNode source = hit.path("_source");
            JsonNode sort = hit.path("sort");
            double distance = sort.path(0).asDouble();
            String address = source.path("address").asText("");
            if (address.isBlank()) {
                address = "坐标 " + source.path("latitude").asDouble() + ", " + source.path("longitude").asDouble();
            }
            pois.add(new NearbyPoi(source.path("id").asText(), source.path("name").asText(),
                    address, source.path("description").asText(""),
                    source.path("latitude").asDouble(), source.path("longitude").asDouble(), distance));
            if (!sort.isEmpty()) {
                List<Object> cursor = new ArrayList<>();
                sort.forEach(value -> cursor.add(value.isNumber() ? value.numberValue() : value.asText()));
                nextCursor = cursor;
            }
        }
        NearbySearchResult result = new NearbySearchResult(pois, hasMore, nextCursor,
                keyword == null ? "" : keyword, latitude, longitude, safeRadius);
        log.info("Nearby POI search completed: resultCount={}, hasMore={}, durationMs={}",
                pois.size(), hasMore, elapsedMillis(startedAt));
        return result;
    }

    private JsonNode request(String method, String endpoint, Object body) {
        Rest5Client restClient = restClientProvider.getIfAvailable();
        if (restClient == null) {
            throw new IllegalStateException("Elasticsearch client is not configured");
        }
        Request request = new Request(method, endpoint);
        long startedAt = System.nanoTime();
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            Response response = restClient.performRequest(request);
            String payload = response.getEntity() == null ? "{}" : EntityUtils.toString(response.getEntity());
            log.debug("Elasticsearch request completed: method={}, endpoint={}, status={}, durationMs={}",
                    method, endpoint, response.getStatusCode(), elapsedMillis(startedAt));
            if (response.getStatusCode() == 404) return objectMapper.createObjectNode();
            if (response.getStatusCode() >= 400) {
                log.error("Elasticsearch request returned an error: method={}, endpoint={}, status={}, durationMs={}",
                        method, endpoint, response.getStatusCode(), elapsedMillis(startedAt));
                throw new IllegalStateException("Nearby POI search failed: " + payload);
            }
            return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (IOException | ParseException exception) {
            log.error("Elasticsearch request failed: method={}, endpoint={}, durationMs={}",
                    method, endpoint, elapsedMillis(startedAt), exception);
            throw new IllegalStateException("Nearby POI search failed", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String indexName() { return agentProperties.getRag().getElasticsearch().getIndexName() + "-geo"; }
}
