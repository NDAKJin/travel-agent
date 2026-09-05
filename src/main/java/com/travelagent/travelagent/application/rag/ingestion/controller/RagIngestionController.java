package com.travelagent.travelagent.application.rag.ingestion.controller;

import com.travelagent.travelagent.application.rag.ingestion.RagIngestionApplication;
import com.travelagent.travelagent.domain.rag.ingestion.RagIngestionTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/rag/documents")
@RequiredArgsConstructor
@Tag(name = "知识库导入", description = "旅行知识文档导入任务管理")
public class RagIngestionController {

    private final RagIngestionApplication application;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入知识库文档", description = "上传一个或多个文档，创建异步解析和向量化任务")
    @ApiResponse(responseCode = "200", description = "导入任务创建成功")
    public List<RagIngestionTaskResponse> importDocuments(
            @Parameter(description = "待导入的文档文件，支持多文件上传", required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))
            @RequestParam("files") MultipartFile[] files) {
        return application.submit(Arrays.asList(files));
    }

    @GetMapping("/import/tasks")
    @Operation(summary = "查询导入任务", description = "返回最近提交的知识库文档导入任务及处理状态")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<RagIngestionTaskResponse> importTasks() {
        return application.list();
    }

    @PostMapping("/import/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "取消导入任务", description = "取消尚未完成的知识库文档导入任务")
    @ApiResponse(responseCode = "204", description = "任务已取消")
    public void cancelTask(@PathVariable long taskId) {
        application.cancel(taskId);
    }

    @PostMapping("/import/tasks/{taskId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "重试导入任务", description = "重新写入 Outbox，由 Canal Kafka Connector 重新投递")
    @ApiResponse(responseCode = "204", description = "任务已重新排队")
    public void retryTask(@PathVariable long taskId) {
        application.retry(taskId);
    }
}
