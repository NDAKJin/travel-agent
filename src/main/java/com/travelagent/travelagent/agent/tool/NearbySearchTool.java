package com.travelagent.travelagent.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.dto.AgentUserLocation;
import com.travelagent.travelagent.agent.dto.NearbySearchResult;
import com.travelagent.travelagent.agent.service.CurrentUserLocationContext;
import com.travelagent.travelagent.agent.service.LocationPermissionContext;
import com.travelagent.travelagent.agent.service.NearbyPoiSearchService;
import com.travelagent.travelagent.agent.service.NearbySearchContext;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NearbySearchTool {
    private final NearbyPoiSearchService nearbyPoiSearchService;

    @Tool(name = "search_nearby_pois", description = "根据微信小程序用户当前位置搜索距离最近的五个景点或文旅服务点。keyword 传入用户意图对应的简洁中文关键词，例如景点、拍照、停车场、卫生间、美食或文化景观。")
    public String searchNearbyPois(String keyword) {
        AgentUserLocation location = CurrentUserLocationContext.get();
        if (location == null) {
            LocationPermissionContext.request();
            return "LOCATION_UNAVAILABLE：当前没有用户位置。请先调用 request_location_permission，再调用 current_user_location 获取位置。";
        }
        NearbySearchResult result = nearbyPoiSearchService.search(location.latitude(), location.longitude(), keyword, 20_000, List.of());
        NearbySearchContext.set(result);
        if (result.pois().isEmpty()) {
            return JSON.toJSONString(Map.of(
                    "status", "no_data",
                    "summary", "当前位置附近暂未找到合适的推荐地点",
                    "data", Map.of("places", List.of()),
                    "warnings", List.of()));
        }
        try {
            return JSON.toJSONString(result);
        } catch (RuntimeException exception) {
            log.error("Failed to serialize nearby POI result", exception);
            return "附近地点搜索已完成，但结果暂时无法格式化。";
        }
    }
}
