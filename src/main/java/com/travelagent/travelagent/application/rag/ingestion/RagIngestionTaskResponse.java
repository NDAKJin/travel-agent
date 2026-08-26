package com.travelagent.travelagent.application.rag.ingestion;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "知识库导入任务")
public record RagIngestionTaskResponse(
        @Schema(description = "任务标识", example = "1") Long id,
        @Schema(description = "文件名") String fileName,
        @Schema(description = "任务状态", example = "SUCCEEDED") String status,
        @Schema(description = "解析出的分块数量") int chunkCount,
        @Schema(description = "成功写入的分块数量") int writtenCount,
        @Schema(description = "失败原因，成功时为空") String error,
        @Schema(description = "创建时间") Instant createdAt,
        @Schema(description = "更新时间") Instant updatedAt) { }
