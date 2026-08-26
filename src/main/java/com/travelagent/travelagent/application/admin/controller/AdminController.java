package com.travelagent.travelagent.application.admin.controller;

import com.travelagent.travelagent.application.admin.dto.AdminConversationDetailResponse;
import com.travelagent.travelagent.application.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.application.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.application.admin.port.in.AdminManagementUseCase;
import com.travelagent.travelagent.application.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "管理查询", description = "用户、会话及 Agent 调用记录查询")
public class AdminController {

    private final AdminManagementUseCase adminManagementService;

    @GetMapping("/wx-users")
    @Operation(summary = "查询微信用户", description = "按昵称或 OpenID 模糊查询微信用户，并分页返回结果")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PageResponse<AdminWxUserResponse> searchWxUsers(
            @Parameter(description = "昵称或 OpenID 关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量，最大 100", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.searchWxUsers(keyword, page, size);
    }

    @GetMapping("/sessions")
    @Operation(summary = "查询会话列表", description = "按微信用户筛选会话，并分页返回会话摘要")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public PageResponse<AdminConversationSummaryResponse> listSessions(
            @Parameter(description = "微信用户标识") @RequestParam(required = false) Long wxUserId,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量，最大 100", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.listSessions(wxUserId, page, size);
    }

    @GetMapping("/sessions/{conversationId}")
    @Operation(summary = "查询会话详情与 Agent 日志", description = "返回会话消息及每条消息关联的 Agent 调用观测记录")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public AdminConversationDetailResponse getSessionDetail(
            @Parameter(description = "会话数据库标识", required = true, example = "1") @PathVariable long conversationId) {
        return adminManagementService.getSessionDetail(conversationId);
    }
}
