package com.travelagent.travelagent.domain.auth.service;

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
    private static final String SEND_LOCK_PREFIX = "auth:email:send-lock:";
    private static final String CODE_KEY_PREFIX = "auth:email:code:";
    private static final String LOCK_VALUE = "1";
    private static final int CODE_BOUND = 1_000_000;
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();
    @Value("${spring.mail.username:}") private String sender;
    @Value("${travel-agent.auth.email-code-ttl:PT10M}") private Duration ttl;
    private static final Duration SEND_INTERVAL = Duration.ofMinutes(1);

    public void send(String email) {
        String normalized = email.trim().toLowerCase();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(SEND_LOCK_PREFIX + normalized, LOCK_VALUE, SEND_INTERVAL);
        if (Boolean.FALSE.equals(acquired)) throw new IllegalArgumentException("验证码获取过于频繁，请一分钟后再试");
        String code = "%06d".formatted(random.nextInt(CODE_BOUND));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + normalized, code, ttl);
        if (sender == null || sender.isBlank()) return; // allow local development without SMTP
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender); message.setTo(normalized); message.setSubject("Travel Agent 邮箱验证码");
        message.setText("您的验证码是：" + code + "，10 分钟内有效。");
        mailSender.send(message);
    }

    public boolean verify(String email, String code) {
        String normalized = email.trim().toLowerCase();
        String stored = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + normalized);
        if (stored == null) return false;
        boolean valid = stored.equals(code.trim());
        if (valid) redisTemplate.delete(CODE_KEY_PREFIX + normalized);
        return valid;
    }
}
