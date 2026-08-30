package com.travelagent.travelagent.application.auth;

import com.travelagent.travelagent.domain.auth.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationApplication {
    private final EmailVerificationService service;

    public void send(String email) { service.send(email); }
    public boolean verify(String email, String code) { return service.verify(email, code); }
}
