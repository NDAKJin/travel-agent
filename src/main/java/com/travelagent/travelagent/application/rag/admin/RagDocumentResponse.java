package com.travelagent.travelagent.application.rag.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "知识库文档")
public record RagDocumentResponse(
        @Schema(description = "文档数据库标识", example = "1") long id,
        @Schema(description = "文档唯一键") String documentKey,
        @Schema(description = "原始文件名") String fileName,
        @Schema(description = "文件媒体类型", example = "application/pdf") String mediaType,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文档作者") String author,
        @Schema(description = "文档关键词") String keywords,
        @Schema(description = "文档摘要") String summary,
        @Schema(description = "适合检索的问题") String questions,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "分块数量", example = "12") int chunkCount,
        @Schema(description = "创建时间") Instant createdAt,
        @Schema(description = "更新时间") Instant updatedAt) { }
