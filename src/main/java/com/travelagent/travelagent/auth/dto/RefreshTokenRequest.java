package com.travelagent.travelagent.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "刷新访问令牌请求")
public record RefreshTokenRequest(@Schema(description = "刷新令牌") @NotBlank String refreshToken) {
}
