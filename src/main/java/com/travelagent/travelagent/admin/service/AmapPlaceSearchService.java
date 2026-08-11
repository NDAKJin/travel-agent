package com.travelagent.travelagent.admin.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
            JSONObject root = JSON.parseObject(body);
            if (!"1".equals(root.getString("status"))) {
                log.warn("Amap place search failed: info={}, infocode={}",
                        root.getString("info"), root.getString("infocode"));
                return List.of();
            }

            List<AdminMapPlaceResponse> places = new ArrayList<>();
            for (Object value : root.getList("pois", JSONObject.class)) {
                JSONObject poi = (JSONObject) value;
                String[] location = poi.getString("location", "").split(",");
                if (location.length != 2) {
                    continue;
                }
                try {
                    places.add(new AdminMapPlaceResponse(
                            poi.getString("id"),
                            poi.getString("name"),
                            poi.getString("address"),
                            Double.parseDouble(location[0]),
                            Double.parseDouble(location[1])));
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping Amap POI with invalid location: {}", poi.getString("location"));
                }
            }
            return places;
        } catch (Exception exception) {
            log.error("Failed to parse Amap place search response", exception);
            throw new IllegalStateException("高德搜索服务返回数据异常", exception);
        }
    }
}
