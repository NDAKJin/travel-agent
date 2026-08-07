package com.travelagent.travelagent.auth.controller;

import com.travelagent.travelagent.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.auth.dto.AuthResponse;
import com.travelagent.travelagent.auth.dto.LogoutRequest;
import com.travelagent.travelagent.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/wx/login")
    public AuthResponse wxLogin(@Valid @RequestBody WxLoginRequest request) {
        log.info("WX login request received: codePresent={}, nicknamePresent={}",
                request.code() != null && !request.code().isBlank(),
                request.nickname() != null && !request.nickname().isBlank());
        return authService.loginWx(request);
    }

    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("Admin login request received: username={}", request.username());
        return authService.loginAdmin(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request received");
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Logout request received");
        authService.logout(request);
    }
}
