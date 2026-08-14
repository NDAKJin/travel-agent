package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端会话消息")
public record AdminConversationMessageResponse(
        @Schema(description = "消息角色", example = "user") String role,
        @Schema(description = "消息内容", example = "推荐杭州两日游") String content) {
}
