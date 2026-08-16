package com.travelagent.travelagent.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信小程序登录请求")
public record WxLoginRequest(
        @Schema(description = "微信登录临时凭证") @NotBlank String code,
        @Schema(description = "用户昵称", example = "旅行者") String nickname,
        @Schema(description = "头像地址") String avatarUrl) {
}
