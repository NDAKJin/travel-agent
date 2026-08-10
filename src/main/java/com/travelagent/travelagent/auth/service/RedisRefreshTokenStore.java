package com.travelagent.travelagent.auth.service;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String KEY_PREFIX = "auth:rt:";
    private static final String KEY_VALUE = "1";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('del', KEYS[1]); return 1; "
                    + "else return 0; end", Long.class);

    private final StringRedisTemplate redisTemplate;

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
    public boolean consumeToken(long userId, String tokenId) {
        Long result = redisTemplate.execute(CONSUME_SCRIPT,
                java.util.List.of(key(userId, tokenId)), KEY_VALUE);
        boolean consumed = Long.valueOf(1L).equals(result);
        log.debug("Consumed refresh token: userId={}, tokenId={}, consumed={}", userId, tokenId, consumed);
        return consumed;
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
