package com.travelagent.travelagent.infrastructure.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Exact-query cache for the RAG tool result. Redis failures are handled by the caller. */
@Component
public class RedisRagToolCache {
    private static final String PREFIX = "rag:tool:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String version;

    public RedisRagToolCache(StringRedisTemplate redis,
            @Value("${travel-agent.rag.cache.ttl:PT2M}") Duration ttl,
            @Value("${travel-agent.rag.cache.version:v1}") String version) {
        this.redis = redis;
        this.ttl = ttl;
        this.version = version;
    }

    public Optional<String> get(String task) {
        String value = redis.opsForValue().get(key(task));
        return Optional.ofNullable(value);
    }

    public void put(String task, String result) {
        if (result != null && !result.isBlank()) {
            redis.opsForValue().set(key(task), result, ttl);
        }
    }

    private String key(String task) {
        return PREFIX + version + ":" + sha256(canonical(task));
    }

    private String canonical(String task) {
        if (task == null) return "";
        try {
            JSONObject json = JSON.parseObject(task);
            if (json != null) return JSON.toJSONString(json);
        } catch (RuntimeException ignored) {
            // Plain-text tool calls are also valid; normalize insignificant whitespace.
        }
        return task.trim().replaceAll("\\s+", " ");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
