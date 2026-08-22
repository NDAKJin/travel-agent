package com.travelagent.travelagent.application.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "AgentSessionSummaryResponse", description = "会话摘要")
public record AgentSessionSummaryResponse(
        @Schema(description = "会话标识", example = "session-1") String sessionId,
        @Schema(description = "会话标题", example = "杭州周末游") String title,
        @Schema(description = "最后一条消息预览") String preview,
        @Schema(description = "已保存消息数", example = "6") int messageCount,
        @Schema(description = "最后更新时间", example = "2026-08-04T09:30:00Z") Instant updatedAt) {
}
