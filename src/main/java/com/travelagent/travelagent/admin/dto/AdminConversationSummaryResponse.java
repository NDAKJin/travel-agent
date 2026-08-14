package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "管理端会话摘要")
public record AdminConversationSummaryResponse(
        @Schema(description = "会话数据库标识", example = "1") long id,
        @Schema(description = "会话标识", example = "session-1") String sessionId,
        @Schema(description = "会话标题", example = "杭州周末游") String title,
        @Schema(description = "最后一条消息预览") String preview,
        @Schema(description = "消息数量", example = "6") int messageCount,
        @Schema(description = "最后更新时间") Instant updatedAt,
        @Schema(description = "会话所属用户") AdminConversationUserResponse user) {
}
