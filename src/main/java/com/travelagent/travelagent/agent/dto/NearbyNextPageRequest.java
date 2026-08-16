package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "附近地点下一页查询请求")
public record NearbyNextPageRequest(
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        @Schema(description = "查询中心纬度", example = "30.2741") double latitude,
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        @Schema(description = "查询中心经度", example = "120.1551") double longitude,
        @Size(max = 100, message = "keyword must not exceed 100 characters")
        @Schema(description = "搜索关键词", example = "景点") String keyword,
        @Min(value = 100, message = "radiusMeters must be at least 100")
        @Max(value = 100000, message = "radiusMeters must not exceed 100000")
        @Schema(description = "搜索半径，单位米；为空时默认 20000", example = "20000") Integer radiusMeters,
        @Schema(description = "上一页返回的下一页游标") List<Object> searchAfter) {
}
