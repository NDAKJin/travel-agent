package com.travelagent.travelagent.application.auth.port.in;

import com.travelagent.travelagent.application.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.application.auth.dto.AuthResponse;
import com.travelagent.travelagent.application.auth.dto.LogoutRequest;
import com.travelagent.travelagent.application.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.application.auth.dto.WxLoginRequest;

public interface AuthUseCase {

    AuthResponse loginWx(WxLoginRequest request);

    AuthResponse loginAdmin(AdminLoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
