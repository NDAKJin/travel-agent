package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "地图地点搜索结果")
public record AdminMapPlaceResponse(
        @Schema(description = "地图地点标识") String id,
        @Schema(description = "地点名称", example = "西湖") String name,
        @Schema(description = "地点地址") String address,
        @Schema(description = "经度", example = "120.1551") double longitude,
        @Schema(description = "纬度", example = "30.2741") double latitude
) {
}
