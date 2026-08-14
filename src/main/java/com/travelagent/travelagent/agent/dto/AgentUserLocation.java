package com.travelagent.travelagent.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户当前位置")
public record AgentUserLocation(
        @Schema(description = "纬度", example = "30.2741") double latitude,
        @Schema(description = "经度", example = "120.1551") double longitude) {
}
