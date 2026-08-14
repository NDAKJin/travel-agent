package com.travelagent.travelagent.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录或刷新令牌响应")
public record AuthResponse(
        @Schema(description = "当前登录用户") AuthUserResponse user,
        @Schema(description = "访问令牌") AuthTokenResponse token) {
}
