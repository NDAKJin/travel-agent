package com.travelagent.travelagent.application.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "AgentSessionDetailResponse", description = "会话详情")
public record AgentSessionDetailResponse(
        @Schema(description = "会话标识", example = "session-1") String sessionId,
        @Schema(description = "会话标题", example = "杭州周末游") String title,
        @Schema(description = "会话消息") List<AgentConversationMessageResponse> messages,
        @Schema(description = "创建时间", example = "2026-08-04T09:00:00Z") Instant createdAt,
        @Schema(description = "最后更新时间", example = "2026-08-04T09:30:00Z") Instant updatedAt) {
}
