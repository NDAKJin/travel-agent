package com.travelagent.travelagent.domain.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AgentChatRequest", description = "旅行助手对话请求")
public record AgentChatRequest(
        @Schema(description = "用户消息", example = "帮我规划杭州两日游")
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,
        @Schema(description = "会话标识；为空时创建新会话", example = "session-1")
        @Size(max = 64, message = "sessionId must not exceed 64 characters") String sessionId,
        @Schema(description = "微信小程序本次请求获取的当前位置")
        @Valid Location location) {

    public AgentChatRequest(String message, String sessionId) {
        this(message, sessionId, null);
    }

    public record Location(
            @Schema(description = "纬度", example = "32.0603")
            @NotNull(message = "location.latitude must not be null")
            @DecimalMin(value = "-90", message = "location.latitude must be between -90 and 90")
            @DecimalMax(value = "90", message = "location.latitude must be between -90 and 90")
            Double latitude,
            @Schema(description = "经度", example = "118.7969")
            @NotNull(message = "location.longitude must not be null")
            @DecimalMin(value = "-180", message = "location.longitude must be between -180 and 180")
            @DecimalMax(value = "180", message = "location.longitude must be between -180 and 180")
            Double longitude,
            @Schema(description = "定位精度，单位米", example = "20")
            @DecimalMin(value = "0", message = "location.accuracy must not be negative")
            Double accuracy) {
    }
}
