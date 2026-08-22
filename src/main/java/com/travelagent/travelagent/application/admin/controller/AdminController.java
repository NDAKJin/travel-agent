package com.travelagent.travelagent.application.admin.controller;

import com.travelagent.travelagent.application.admin.dto.AdminConversationDetailResponse;
import com.travelagent.travelagent.application.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.application.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.application.admin.port.in.AdminManagementUseCase;
import com.travelagent.travelagent.application.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
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
public class AdminController {

    private final AdminManagementUseCase adminManagementService;

    @GetMapping("/wx-users")
    @Operation(summary = "查询用户")
    public PageResponse<AdminWxUserResponse> searchWxUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.searchWxUsers(keyword, page, size);
    }

    @GetMapping("/sessions")
    @Operation(summary = "查询会话")
    public PageResponse<AdminConversationSummaryResponse> listSessions(
            @RequestParam(required = false) Long wxUserId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.listSessions(wxUserId, page, size);
    }

    @GetMapping("/sessions/{conversationId}")
    @Operation(summary = "查询会话详情与 Agent 日志")
    public AdminConversationDetailResponse getSessionDetail(@PathVariable long conversationId) {
        return adminManagementService.getSessionDetail(conversationId);
    }
}
