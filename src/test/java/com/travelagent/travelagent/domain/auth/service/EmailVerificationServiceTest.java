package com.travelagent.travelagent.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

class EmailVerificationServiceTest {
    @Test void sendsAndVerifiesCode() {
        JavaMailSender mail = mock(JavaMailSender.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);
        when(values.get("auth:email:code:a@example.com")).thenReturn("123456");
        EmailVerificationService service = new EmailVerificationService(mail, redis);
        service.send("A@Example.com");
        assertThat(service.verify("a@example.com", "123456")).isTrue();
        assertThat(service.verify("a@example.com", "000000")).isFalse();
        verify(values).setIfAbsent(eq("auth:email:send-lock:a@example.com"), eq("1"), any());
        verify(redis).delete("auth:email:code:a@example.com");
    }
}
