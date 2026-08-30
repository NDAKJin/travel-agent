package com.travelagent.travelagent.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "管理端会话详情")
public record AdminConversationDetailResponse(
        @Schema(description = "会话标识") String sessionId,
        @Schema(description = "会话标题") String title,
        @Schema(description = "会话消息及观测日志") List<AdminConversationMessageResponse> messages,
        @Schema(description = "创建时间") Instant createdAt,
        @Schema(description = "更新时间") Instant updatedAt) {
}
