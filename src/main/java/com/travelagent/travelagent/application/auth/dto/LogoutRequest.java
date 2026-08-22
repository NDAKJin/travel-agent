package com.travelagent.travelagent.application.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "退出登录请求")
public record LogoutRequest(@Schema(description = "需要失效的刷新令牌") @NotBlank String refreshToken) {
}
