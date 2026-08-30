package com.travelagent.travelagent.application.auth;
import com.travelagent.travelagent.domain.auth.dto.*;
import com.travelagent.travelagent.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class AuthApplication {
 private final AuthService service;
 public AuthResponse loginWx(WxLoginRequest r){return service.loginWx(r);} public AuthResponse loginAdmin(AdminLoginRequest r){return service.loginAdmin(r);}
 public AuthResponse loginEmail(EmailAuthRequest r){return service.loginEmail(r);} public AuthResponse registerEmail(EmailAuthRequest r){return service.registerEmail(r);}
 public AuthResponse refresh(RefreshTokenRequest r){return service.refresh(r);} public void logout(LogoutRequest r){service.logout(r);}
}
