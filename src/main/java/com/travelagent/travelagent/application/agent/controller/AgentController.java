package com.travelagent.travelagent.application.agent.controller;

import com.travelagent.travelagent.domain.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.domain.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.domain.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.application.agent.AgentApplication;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "旅行助手", description = "旅行问答、会话与附近地点推荐")
@Slf4j
@RequiredArgsConstructor
public class AgentController {

    private final AgentApplication agentApplication;

    @PostMapping("/chat")
    @Operation(summary = "与旅行助手对话", description = "通过会话标识保留多轮上下文，可附带当前位置")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "对话成功", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "400", description = "请求参数无效", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request, Authentication authentication) {
        log.info("Agent chat request received: sessionId={}, messageLength={}",
                request.sessionId(),
                request.message() == null ? 0 : request.message().length());
        return agentApplication.chat(currentUser(authentication), request);
    }

    @PostMapping("/sessions")
    @Operation(summary = "创建会话", description = "为当前用户创建空会话")
    @ApiResponse(responseCode = "200", description = "创建成功")
    public AgentSessionSummaryResponse createSession(Authentication authentication) {
        return agentApplication.createSession(currentUser(authentication));
    }

    @GetMapping("/sessions")
    @Operation(summary = "查询会话列表", description = "查询当前用户的历史会话")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<AgentSessionSummaryResponse> listSessions(Authentication authentication) {
        return agentApplication.listSessions(currentUser(authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "查询会话详情", description = "查询当前用户的一段历史会话")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public AgentSessionDetailResponse getSession(@Parameter(description = "会话标识") @PathVariable String sessionId, Authentication authentication) {
        return agentApplication.getSession(currentUser(authentication), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话", description = "删除当前用户的一段会话")
    @ApiResponse(responseCode = "200", description = "删除成功")
    public void deleteSession(@Parameter(description = "会话标识") @PathVariable String sessionId, Authentication authentication) {
        agentApplication.deleteSession(currentUser(authentication), sessionId);
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
