package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "附近地点搜索结果")
public record NearbySearchResult(
        @Schema(description = "当前页地点列表") List<NearbyPoi> pois,
        @Schema(description = "是否还有下一页") boolean hasMore,
        @Schema(description = "下一页游标") List<Object> searchAfter,
        @Schema(description = "搜索关键词") String keyword,
        @Schema(description = "查询中心纬度") double latitude,
        @Schema(description = "查询中心经度") double longitude,
        @Schema(description = "搜索半径，单位米") int radiusMeters) {
}
