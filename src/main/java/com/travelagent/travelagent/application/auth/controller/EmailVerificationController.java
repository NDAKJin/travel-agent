package com.travelagent.travelagent.application.auth.controller;

import com.travelagent.travelagent.application.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService service;

    @PostMapping("/send-code")
    public ResponseEntity<Void> send(@Valid @RequestBody EmailRequest request) {
        service.send(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-code")
    public Map<String, Boolean> verify(@Valid @RequestBody VerifyRequest request) {
        return Map.of("valid", service.verify(request.email(), request.code()));
    }

    public record EmailRequest(@Email @NotBlank String email) { }
    public record VerifyRequest(@Email @NotBlank String email, @NotBlank String code) { }
}
