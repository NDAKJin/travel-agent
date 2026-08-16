package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "景点信息")
public record AdminScenicSpotResponse(
        @Schema(description = "景点标识") String id,
        @Schema(description = "景点名称") String name,
        @Schema(description = "所属城市") String city,
        @Schema(description = "景点分类") String category,
        @Schema(description = "景点介绍") String description,
        @Schema(description = "经度") double longitude,
        @Schema(description = "纬度") double latitude,
        @Schema(description = "最后更新时间") Instant updatedAt) {
}
