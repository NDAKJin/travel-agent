package com.travelagent.travelagent.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Agent 调用观测记录")
public record AdminAgentObservationResponse(
        @Schema(description = "Agent 名称") String agentName,
        @Schema(description = "Agent 输入") String input,
        @Schema(description = "Agent 输出") String output,
        @Schema(description = "输入 Token 数") Integer promptTokens,
        @Schema(description = "输出 Token 数") Integer completionTokens,
        @Schema(description = "总 Token 数") Integer totalTokens,
        @Schema(description = "下一步决策") String nextDecision,
        @Schema(description = "调用时间") Instant createdAt) {
}
