package com.travelagent.travelagent.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.admin.dto.AdminMapPlaceResponse;
import com.travelagent.travelagent.config.AmapProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmapPlaceSearchService {

    private final AmapProperties amapProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create("https://restapi.amap.com");

    public List<AdminMapPlaceResponse> search(String keyword) {
        if (amapProperties.getWebServiceKey() == null || amapProperties.getWebServiceKey().isBlank()) {
            throw new IllegalStateException("未配置高德 Web 服务 Key，请设置 AMAP_WEB_SERVICE_KEY");
        }

        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/place/text")
                        .queryParam("key", amapProperties.getWebServiceKey())
                        .queryParam("keywords", keyword)
                        .queryParam("city", amapProperties.getCity())
                        .queryParam("citylimit", "false")
                        .queryParam("offset", amapProperties.getPageSize())
                        .queryParam("page", 1)
                        .queryParam("extensions", "base")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("Amap place search failed: info={}, infocode={}",
                        root.path("info").asText(), root.path("infocode").asText());
                return List.of();
            }

            List<AdminMapPlaceResponse> places = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                String[] location = poi.path("location").asText("").split(",");
                if (location.length != 2) {
                    continue;
                }
                try {
                    places.add(new AdminMapPlaceResponse(
                            poi.path("id").asText(),
                            poi.path("name").asText(),
                            poi.path("address").asText(),
                            Double.parseDouble(location[0]),
                            Double.parseDouble(location[1])));
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping Amap POI with invalid location: {}", poi.path("location").asText());
                }
            }
            return places;
        } catch (Exception exception) {
            log.error("Failed to parse Amap place search response", exception);
            throw new IllegalStateException("高德搜索服务返回数据异常", exception);
        }
    }
}
