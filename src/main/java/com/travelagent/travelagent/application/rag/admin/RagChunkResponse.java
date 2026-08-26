package com.travelagent.travelagent.application.rag.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "知识库文档分块")
public record RagChunkResponse(
        @Schema(description = "分块数据库标识", example = "1") long id,
        @Schema(description = "所属文档标识", example = "1") long documentId,
        @Schema(description = "文档唯一键") String documentKey,
        @Schema(description = "原始文件名") String fileName,
        @Schema(description = "分块序号", example = "0") int chunkIndex,
        @Schema(description = "内容起始偏移量") int startOffset,
        @Schema(description = "内容结束偏移量") int endOffset,
        @Schema(description = "分块正文") String content,
        @Schema(description = "分块关键词") String keywords,
        @Schema(description = "分块摘要") String summary,
        @Schema(description = "适合检索的问题") String questions,
        @Schema(description = "是否启用") boolean enabled) { }
