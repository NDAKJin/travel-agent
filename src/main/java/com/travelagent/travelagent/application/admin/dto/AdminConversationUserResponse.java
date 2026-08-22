package com.travelagent.travelagent.application.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "会话所属用户")
public record AdminConversationUserResponse(
        @Schema(description = "用户标识", example = "1") long id,
        @Schema(description = "用户类型", example = "wx") String userType,
        @Schema(description = "用户主体标识") String subject,
        @Schema(description = "用户展示名称") String displayName) {
}
