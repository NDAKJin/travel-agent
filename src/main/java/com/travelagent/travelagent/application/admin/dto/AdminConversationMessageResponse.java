package com.travelagent.travelagent.application.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "管理端会话消息")
public record AdminConversationMessageResponse(
        @Schema(description = "消息数据库标识") long id,
        @Schema(description = "消息角色", example = "user") String role,
        @Schema(description = "消息内容", example = "推荐杭州两日游") String content,
        @Schema(description = "该用户消息关联的 Agent 观测日志") List<AdminAgentObservationResponse> observations) {
}
