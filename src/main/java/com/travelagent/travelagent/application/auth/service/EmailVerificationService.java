package com.travelagent.travelagent.application.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final JavaMailSender mailSender;
    private final Map<String, Code> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    @Value("${spring.mail.username:}") private String sender;
    @Value("${travel-agent.auth.email-code-ttl:PT10M}") private Duration ttl;

    public void send(String email) {
        String normalized = email.trim().toLowerCase();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        codes.put(normalized, new Code(code, Instant.now().plus(ttl)));
        if (sender == null || sender.isBlank()) return; // allow local development without SMTP
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender); message.setTo(normalized); message.setSubject("Travel Agent 注册验证码");
        message.setText("您的验证码是：" + code + "，10 分钟内有效。");
        mailSender.send(message);
    }

    public boolean verify(String email, String code) {
        String normalized = email.trim().toLowerCase();
        Code stored = codes.get(normalized);
        if (stored == null || stored.expiresAt().isBefore(Instant.now())) { codes.remove(normalized); return false; }
        boolean valid = stored.value().equals(code.trim());
        if (valid) codes.remove(normalized);
        return valid;
    }
    private record Code(String value, Instant expiresAt) { }
}
