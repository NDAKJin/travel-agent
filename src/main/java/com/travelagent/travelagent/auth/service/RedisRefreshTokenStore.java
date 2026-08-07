package com.travelagent.travelagent.auth.service;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String KEY_PREFIX = "auth:rt:";
    private static final String KEY_VALUE = "1";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void storeToken(long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId, tokenId), KEY_VALUE, ttl);
        log.debug("Stored refresh token: userId={}, tokenId={}", userId, tokenId);
    }

    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        boolean valid = Objects.equals(KEY_VALUE, redisTemplate.opsForValue().get(key(userId, tokenId)));
        log.debug("Validated refresh token: userId={}, tokenId={}, valid={}", userId, tokenId, valid);
        return valid;
    }

    @Override
    public void revokeToken(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
        log.debug("Revoked refresh token: userId={}, tokenId={}", userId, tokenId);
    }

    @Override
    public void revokeAll(long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.debug("Revoked all refresh tokens: userId={}, tokenCount={}", userId, keys == null ? 0 : keys.size());
    }

    private String key(long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }
}
