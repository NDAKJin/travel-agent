package com.travelagent.travelagent.application.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "访问与刷新令牌")
public record AuthTokenResponse(
        @Schema(description = "访问令牌") String accessToken,
        @Schema(description = "访问令牌过期时间") Instant accessTokenExpiresAt,
        @Schema(description = "刷新令牌") String refreshToken,
        @Schema(description = "刷新令牌过期时间") Instant refreshTokenExpiresAt) {
}
