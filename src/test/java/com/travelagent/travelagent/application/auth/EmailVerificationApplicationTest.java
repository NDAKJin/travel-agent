package com.travelagent.travelagent.application.auth;

import static org.mockito.Mockito.*;
import com.travelagent.travelagent.domain.auth.service.EmailVerificationService;
import org.junit.jupiter.api.Test;

class EmailVerificationApplicationTest {
    @Test void delegatesVerificationOperations() {
        EmailVerificationService service = mock(EmailVerificationService.class);
        EmailVerificationApplication app = new EmailVerificationApplication(service);
        app.send("a@example.com"); app.verify("a@example.com", "123456");
        verify(service).send("a@example.com"); verify(service).verify("a@example.com", "123456");
    }
}
