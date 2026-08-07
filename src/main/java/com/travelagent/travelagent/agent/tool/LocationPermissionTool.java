package com.travelagent.travelagent.agent.tool;

import com.travelagent.travelagent.agent.service.LocationPermissionContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class LocationPermissionTool {

    @Tool(name = "request_location_permission", description = "请求微信小程序向用户发起当前位置授权。当 current_user_location 返回位置不可用时调用；授权完成后必须再次调用 current_user_location。")
    public String requestLocationPermission() {
        LocationPermissionContext.request();
        return "LOCATION_PERMISSION_REQUIRED：请小程序向用户发起位置授权。授权完成后，请再次调用 current_user_location 获取当前位置。";
    }
}
