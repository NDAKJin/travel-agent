package com.travelagent.travelagent.application.auth;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.auth.dto.EmailAuthRequest;
import com.travelagent.travelagent.domain.auth.service.AuthService;
import org.junit.jupiter.api.Test;

class AuthApplicationTest {
    @Test void delegatesEmailAuthentication() {
        AuthService service = mock(AuthService.class);
        AuthApplication app = new AuthApplication(service);
        EmailAuthRequest request = mock(EmailAuthRequest.class);
        app.loginEmail(request); app.registerEmail(request);
        verify(service).loginEmail(request); verify(service).registerEmail(request);
    }
}
