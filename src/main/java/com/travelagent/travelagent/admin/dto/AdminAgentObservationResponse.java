package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "管理端 Agent 调用观测记录")
public record AdminAgentObservationResponse(
        @Schema(description = "调用节点或专家名称") String agentName,
        @Schema(description = "阶段", example = "llm") String phase,
        @Schema(description = "状态", example = "success") String status,
        @Schema(description = "模型名称") String model,
        @Schema(description = "LLM 输入") String llmInput,
        @Schema(description = "LLM 输出") String llmOutput,
        @Schema(description = "输入 Token 数") Integer promptTokens,
        @Schema(description = "输出 Token 数") Integer completionTokens,
        @Schema(description = "总 Token 数") Integer totalTokens,
        @Schema(description = "耗时（毫秒）") Long durationMs,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "记录时间") Instant createdAt) {
}
