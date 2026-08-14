package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "附近推荐地点")
public record NearbyPoi(
        @Schema(description = "地点标识") String id,
        @Schema(description = "地点名称", example = "西湖") String name,
        @Schema(description = "地点地址") String address,
        @Schema(description = "地点介绍") String description,
        @Schema(description = "纬度") double latitude,
        @Schema(description = "经度") double longitude,
        @Schema(description = "距用户距离，单位米") double distanceMeters) {
}
