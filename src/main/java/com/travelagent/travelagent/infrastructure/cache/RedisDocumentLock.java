package com.travelagent.travelagent.infrastructure.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis distributed lock with lease renewal and multi-identity locking. */
@Component
public class RedisDocumentLock {
    private static final String PREFIX = "rag:document-lock:";
    private static final ScheduledExecutorService WATCHDOG = Executors.newScheduledThreadPool(1,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "rag-document-lock-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final long waitMillis;
    private final Duration lease;

    public RedisDocumentLock(StringRedisTemplate redis,
            @Value("${travel-agent.rag.document-lock.wait-ms:30000}") long waitMillis,
            @Value("${travel-agent.rag.document-lock.lease:PT10M}") Duration lease) {
        if (waitMillis < 0) throw new IllegalArgumentException("RAG document lock wait must not be negative");
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("RAG document lock lease must be positive");
        }
        this.redis = redis;
        this.waitMillis = waitMillis;
        this.lease = lease;
    }

    /** Locks both the normalized file name and content hash to cover either identity. */
    public LockHandle acquire(String fileName, String documentKey) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("RAG document file name must not be blank");
        }
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("RAG document key must not be blank");
        }
        Set<String> identities = new TreeSet<>();
        identities.add("file:" + fileName.trim().toLowerCase(Locale.ROOT));
        identities.add("document:" + documentKey.trim().toLowerCase(Locale.ROOT));
        List<String> keys = identities.stream().map(this::key).toList();
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        List<String> acquired = new ArrayList<>();
        try {
            for (String lockKey : keys) {
                acquireOne(lockKey, token, deadline, fileName);
                acquired.add(lockKey);
            }
        } catch (RuntimeException exception) {
            release(acquired, token);
            throw exception;
        }
        AtomicBoolean closed = new AtomicBoolean();
        long interval = Math.max(1_000L, lease.toMillis() / 3L);
        ScheduledFuture<?> renewal = WATCHDOG.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            for (String lockKey : keys) {
                Long renewed = redis.execute(RENEW, List.of(lockKey), token, Long.toString(lease.toMillis()));
                if (!Long.valueOf(1L).equals(renewed)) return;
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                renewal.cancel(false);
                release(keys, token);
            }
        };
    }

    /** Backward-compatible file-only lock for callers that do not yet have a content hash. */
    public LockHandle acquire(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("RAG document file name must not be blank");
        }
        Set<String> identities = Set.of("file:" + fileName.trim().toLowerCase(Locale.ROOT));
        return acquireIdentities(identities, fileName);
    }

    private LockHandle acquireIdentities(Set<String> identities, String displayName) {
        List<String> keys = identities.stream().sorted().map(this::key).toList();
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        List<String> acquired = new ArrayList<>();
        try {
            for (String lockKey : keys) {
                acquireOne(lockKey, token, deadline, displayName);
                acquired.add(lockKey);
            }
        } catch (RuntimeException exception) {
            release(acquired, token);
            throw exception;
        }
        AtomicBoolean closed = new AtomicBoolean();
        long interval = Math.max(1_000L, lease.toMillis() / 3L);
        ScheduledFuture<?> renewal = WATCHDOG.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            for (String lockKey : keys) {
                Long renewed = redis.execute(RENEW, List.of(lockKey), token, Long.toString(lease.toMillis()));
                if (!Long.valueOf(1L).equals(renewed)) return;
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                renewal.cancel(false);
                release(keys, token);
            }
        };
    }

    private void acquireOne(String lockKey, String token, long deadline, String displayName) {
        while (true) {
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, lease);
            if (Boolean.TRUE.equals(acquired)) return;
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out acquiring RAG document lock: " + displayName);
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted acquiring RAG document lock: " + displayName, ex);
            }
        }
    }

    private void release(List<String> keys, String token) {
        for (String lockKey : keys) redis.execute(UNLOCK, List.of(lockKey), token);
    }

    private String key(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @FunctionalInterface
    public interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }
}
