package com.travelagent.travelagent.application.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();
    @Value("${spring.mail.username:}") private String sender;
    @Value("${travel-agent.auth.email-code-ttl:PT10M}") private Duration ttl;
    private static final Duration SEND_INTERVAL = Duration.ofMinutes(1);

    public void send(String email) {
        String normalized = email.trim().toLowerCase();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent("auth:email:send-lock:" + normalized, "1", SEND_INTERVAL);
        if (Boolean.FALSE.equals(acquired)) throw new IllegalArgumentException("验证码获取过于频繁，请一分钟后再试");
        String code = "%06d".formatted(random.nextInt(1_000_000));
        redisTemplate.opsForValue().set("auth:email:code:" + normalized, code, ttl);
        if (sender == null || sender.isBlank()) return; // allow local development without SMTP
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender); message.setTo(normalized); message.setSubject("Travel Agent 注册验证码");
        message.setText("您的验证码是：" + code + "，10 分钟内有效。");
        mailSender.send(message);
    }

    public boolean verify(String email, String code) {
        String normalized = email.trim().toLowerCase();
        String stored = redisTemplate.opsForValue().get("auth:email:code:" + normalized);
        if (stored == null) return false;
        boolean valid = stored.equals(code.trim());
        if (valid) redisTemplate.delete("auth:email:code:" + normalized);
        return valid;
    }
}
