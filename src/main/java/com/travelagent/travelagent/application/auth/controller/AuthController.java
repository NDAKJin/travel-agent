package com.travelagent.travelagent.application.auth.controller;

import com.travelagent.travelagent.application.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.application.auth.dto.AuthResponse;
import com.travelagent.travelagent.application.auth.dto.LogoutRequest;
import com.travelagent.travelagent.application.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.application.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.application.auth.dto.EmailAuthRequest;
import com.travelagent.travelagent.application.auth.port.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "微信用户与管理端登录认证")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authService;

    @PostMapping("/wx/login")
    @Operation(summary = "微信小程序登录", description = "使用微信临时凭证换取系统访问令牌")
    @ApiResponse(responseCode = "200", description = "登录成功")
    public AuthResponse wxLogin(@Valid @RequestBody WxLoginRequest request) {
        log.info("WX login request received: codePresent={}, nicknamePresent={}",
                request.code() != null && !request.code().isBlank(),
                request.nickname() != null && !request.nickname().isBlank());
        return authService.loginWx(request);
    }

    @PostMapping("/admin/login")
    @Operation(summary = "管理端登录", description = "使用管理员用户名和密码换取系统访问令牌")
    @ApiResponse(responseCode = "200", description = "登录成功")
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("Admin login request received: username={}", request.username());
        return authService.loginAdmin(request);
    }

    @PostMapping("/email/login")
    public AuthResponse emailLogin(@Valid @RequestBody EmailAuthRequest request) { return authService.loginEmail(request); }

    @PostMapping("/email/register")
    public AuthResponse emailRegister(@Valid @RequestBody EmailAuthRequest request) { return authService.registerEmail(request); }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌", description = "使用刷新令牌获取新的访问令牌")
    @ApiResponse(responseCode = "200", description = "刷新成功")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request received");
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "使指定刷新令牌立即失效")
    @ApiResponse(responseCode = "200", description = "退出成功")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Logout request received");
        authService.logout(request);
    }
}
