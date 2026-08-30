package com.travelagent.travelagent.application.auth.port.in;

import com.travelagent.travelagent.application.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.application.auth.dto.AuthResponse;
import com.travelagent.travelagent.application.auth.dto.LogoutRequest;
import com.travelagent.travelagent.application.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.application.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.application.auth.dto.EmailAuthRequest;

public interface AuthUseCase {

    AuthResponse loginWx(WxLoginRequest request);

    AuthResponse loginAdmin(AdminLoginRequest request);
    AuthResponse loginEmail(EmailAuthRequest request);
    AuthResponse registerEmail(EmailAuthRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
