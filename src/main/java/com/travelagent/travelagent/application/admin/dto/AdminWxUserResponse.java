package com.travelagent.travelagent.application.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "微信用户信息")
public record AdminWxUserResponse(
        @Schema(description = "用户标识", example = "1") long id,
        @Schema(description = "微信 OpenID") String openId,
        @Schema(description = "用户昵称") String nickname,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "最后更新时间") Instant updatedAt) {
}
