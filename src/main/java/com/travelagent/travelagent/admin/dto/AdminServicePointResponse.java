package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "便民服务点信息")
public record AdminServicePointResponse(
        @Schema(description = "服务点标识") String id,
        @Schema(description = "服务点名称") String name,
        @Schema(description = "服务点分类") String category,
        @Schema(description = "服务说明") String description,
        @Schema(description = "详细地址") String address,
        @Schema(description = "经度") double longitude,
        @Schema(description = "纬度") double latitude,
        @Schema(description = "最后更新时间") Instant updatedAt) {
}
