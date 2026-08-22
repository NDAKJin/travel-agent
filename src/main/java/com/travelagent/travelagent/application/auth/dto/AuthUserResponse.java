package com.travelagent.travelagent.application.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前登录用户")
public record AuthUserResponse(
        @Schema(description = "用户标识", example = "1") Long id,
        @Schema(description = "用户类型", example = "admin") String userType,
        @Schema(description = "用户主体标识") String subject,
        @Schema(description = "用户展示名称") String displayName) {
}
