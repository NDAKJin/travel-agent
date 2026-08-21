package com.travelagent.travelagent.application.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "管理端登录请求")
public record AdminLoginRequest(
        @Schema(description = "管理员用户名", example = "admin") @NotBlank String username,
        @Schema(description = "管理员密码", example = "******") @NotBlank String password) {
}
