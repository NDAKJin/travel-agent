package com.travelagent.travelagent.agent.tool;

import com.travelagent.travelagent.agent.dto.AgentUserLocation;
import com.travelagent.travelagent.agent.service.CurrentUserLocationContext;
import com.travelagent.travelagent.agent.service.LocationPermissionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CurrentUserLocationTool {

    @Tool(name = "current_user_location", description = "获取微信小程序用户提供的当前地理位置。当用户询问附近景点、附近推荐，或要求基于当前位置规划路线时使用。")
    public String currentUserLocation() {
        log.info("Executing tool: current_user_location");
        AgentUserLocation location = CurrentUserLocationContext.get();
        if (location == null) {
            LocationPermissionContext.request();
            return "LOCATION_UNAVAILABLE：当前位置不可用。请先调用 request_location_permission 请求小程序授权，然后再次调用 current_user_location。";
        }
        log.info("Executing tool: current_user_location, latitude={}, longitude={}", location.latitude(), location.longitude());
        return "当前用户位置：纬度=%s，经度=%s".formatted(location.latitude(), location.longitude());
    }
}
