package com.travelagent.travelagent.application.rag.admin.controller;

import com.travelagent.travelagent.application.rag.admin.RagAdminApplication;
import com.travelagent.travelagent.domain.rag.dto.RagChunkResponse;
import com.travelagent.travelagent.domain.rag.dto.RagChunkUpdateRequest;
import com.travelagent.travelagent.domain.rag.dto.RagDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "旅行知识文档和分块的查询与启停管理")
public class RagAdminController {

    private final RagAdminApplication ragAdminApplication;

    @GetMapping("/documents")
    @Operation(summary = "查询知识库文档", description = "按文件名、标题或文档关键词筛选已导入文档")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<RagDocumentResponse> documents(
            @Parameter(description = "文件名、标题或关键词") @RequestParam(required = false) String keyword) {
        return ragAdminApplication.documents(keyword);
    }

    @GetMapping("/chunks")
    @Operation(summary = "查询知识库分块", description = "按文档、关键词和启用状态筛选分块内容")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<RagChunkResponse> chunks(
            @Parameter(description = "文档标识") @RequestParam(required = false) Long documentId,
            @Parameter(description = "分块内容或元数据关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "启用状态：1 启用，0 禁用") @RequestParam(required = false) Integer enabled) {
        return ragAdminApplication.chunks(documentId, keyword, enabled);
    }

    @PatchMapping("/documents/{documentId}/enable")
    @Operation(summary = "启用或禁用文档")
    @ApiResponse(responseCode = "204", description = "更新成功")
    public ResponseEntity<Void> toggleDocument(
            @Parameter(description = "文档标识", required = true, example = "1") @PathVariable long documentId,
            @Parameter(description = "是否启用", required = true, example = "true") @RequestParam boolean enabled) {
        ragAdminApplication.toggleDocument(documentId, enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chunks/{chunkId}/enable")
    @Operation(summary = "启用或禁用分块")
    @ApiResponse(responseCode = "204", description = "更新成功")
    public ResponseEntity<Void> toggleChunk(
            @Parameter(description = "分块标识", required = true, example = "1") @PathVariable long chunkId,
            @Parameter(description = "是否启用", required = true, example = "true") @RequestParam boolean enabled) {
        ragAdminApplication.toggleChunk(chunkId, enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chunks/{chunkId}")
    @Operation(summary = "鏇存柊 Chunk 鍐呭")
    @ApiResponse(responseCode = "204", description = "鏇存柊鎴愬姛")
    public ResponseEntity<Void> updateChunk(
            @PathVariable long chunkId,
            @RequestBody RagChunkUpdateRequest request) {
        ragAdminApplication.updateChunkContent(chunkId, request.content());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/chunks/batch-enable")
    @Operation(summary = "批量启用或禁用分块")
    @ApiResponse(responseCode = "204", description = "更新成功")
    public ResponseEntity<Void> batchToggleChunks(
            @Parameter(description = "是否启用", required = true, example = "true") @RequestParam boolean enabled,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "待更新的分块标识列表", required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @Schema(type = "array", implementation = Long.class)))
            @RequestBody List<Long> chunkIds) {
        ragAdminApplication.batchToggleChunks(chunkIds, enabled);
        return ResponseEntity.noContent().build();
    }
}
