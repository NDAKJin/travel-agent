package com.travelagent.travelagent.infrastructure.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.infrastructure.planning.port.RoutePlanSemanticCache;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.stereotype.Component;

/** Redis Stack vector cache. TTL is applied to every route entry. */
@Component
public class RedisRoutePlanSemanticCache implements RoutePlanSemanticCache {
    private static final Logger log = LoggerFactory.getLogger(RedisRoutePlanSemanticCache.class);
    private static final byte[] PREFIX = bytes("route:semantic:");
    private static final String INDEX = "route_plan_cache_idx";
    private static final String VECTOR = "vector";
    private static final String PLAN = "routePlan";
    private static final String REQUIREMENTS = "requirements";
    private static final String EXPIRES_AT = "expiresAt";

    private final RedisConnectionFactory connectionFactory;
    private final EmbeddingModel embeddingModel;
    private final Duration ttl;
    private final double threshold;
    private final Executor writeExecutor;
    private volatile int dimensions;

    public RedisRoutePlanSemanticCache(RedisConnectionFactory connectionFactory,
                                       EmbeddingModel embeddingModel,
                                       @Value("${travel-agent.route-cache.ttl:PT24H}") Duration ttl,
                                       @Value("${travel-agent.route-cache.similarity-threshold:0.92}") double threshold,
                                       @Qualifier("routeCacheExecutor") Executor writeExecutor) {
        this.connectionFactory = connectionFactory;
        this.embeddingModel = embeddingModel;
        this.ttl = ttl;
        this.threshold = threshold;
        this.writeExecutor = writeExecutor;
    }

    @Override
    public Optional<Hit> find(String requirements) {
        byte[] query = vector(requirements);
        return withCommands(redis -> {
            ensureIndex(redis, query.length / Float.BYTES);
            Object raw = redis.commands().execute("FT.SEARCH", bytes(INDEX),
                    bytes("*=>[KNN 1 @vector $query_vector AS score]"),
                    bytes("PARAMS"), bytes("2"), bytes("query_vector"), query,
                    bytes("SORTBY"), bytes("score"), bytes("ASC"),
                    bytes("RETURN"), bytes("3"), bytes("routePlan"), bytes("requirements"), bytes("score"),
                    bytes("DIALECT"), bytes("2"));
            List<?> resultList = raw instanceof List<?> list ? list : List.of();
            if (resultList.size() < 3) return Optional.empty();
            List<?> fields = resultList.get(2) instanceof List<?> list ? list : List.of();
            String cachedRequirements = null;
            String plan = null;
            Double distance = null;
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                String name = text(fields.get(i));
                String value = text(fields.get(i + 1));
                if (PLAN.equals(name)) plan = value;
                else if (REQUIREMENTS.equals(name)) cachedRequirements = value;
                else if ("score".equals(name)) {
                    try { distance = Double.valueOf(value); } catch (RuntimeException ignored) { }
                }
            }
            // RediSearch returns cosine distance (0 = identical), not similarity.
            double similarity = 1d - (distance == null ? 1d : distance);
            if (similarity < threshold) return Optional.empty();
            if (!matchesMandatoryRequirements(requirements, cachedRequirements)) return Optional.empty();
            return plan == null ? Optional.empty() : Optional.of(new Hit(plan, similarity));
        });
    }

    @Override
    public void put(String requirements, String routePlan) {
        if (requirements == null || routePlan == null) return;
        writeExecutor.execute(() -> putSynchronously(requirements, routePlan));
    }

    private void putSynchronously(String requirements, String routePlan) {
        try {
            byte[] query = vector(requirements);
            withCommands(redis -> {
                ensureIndex(redis, query.length / Float.BYTES);
                byte[] key = bytes(new String(PREFIX, StandardCharsets.UTF_8) + sha256(canonical(requirements)));
                redis.hashCommands().hSet(key, bytes(VECTOR), query);
                redis.hashCommands().hSet(key, bytes(PLAN), bytes(routePlan));
                redis.hashCommands().hSet(key, bytes(REQUIREMENTS), bytes(requirements));
                redis.hashCommands().hSet(key, bytes(EXPIRES_AT), bytes(Long.toString(System.currentTimeMillis() + ttl.toMillis())));
                redis.keyCommands().expire(key, ttl);
                return null;
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to write route semantic cache asynchronously", exception);
        }
    }

    private byte[] vector(String requirements) {
        String text = canonical(requirements);
        return floats(embeddingModel.embed(text));
    }

    private String canonical(String requirements) {
        JSONObject value = JSON.parseObject(requirements);
        return value == null ? requirements : JSON.toJSONString(value);
    }

    private void ensureIndex(RedisConnection redis, int dimensions) {
        if (this.dimensions == dimensions) return;
        synchronized (this) {
            if (this.dimensions == dimensions) return;
            try {
                redis.commands().execute("FT.CREATE", bytes(INDEX), bytes("ON"), bytes("HASH"),
                        bytes("PREFIX"), bytes("1"), PREFIX, bytes("SCHEMA"),
                        bytes(VECTOR), bytes("VECTOR"), bytes("HNSW"), bytes("6"),
                        bytes("TYPE"), bytes("FLOAT32"), bytes("DIM"), bytes(Integer.toString(dimensions)),
                        bytes("DISTANCE_METRIC"), bytes("COSINE"), bytes("M"), bytes("16"), bytes("EF_CONSTRUCTION"), bytes("200"),
                        bytes(REQUIREMENTS), bytes("TAG"), bytes(EXPIRES_AT), bytes("NUMERIC"));
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                if (message == null || !message.toLowerCase().contains("index already exists")) throw exception;
            }
            this.dimensions = dimensions;
        }
    }

    private boolean matchesMandatoryRequirements(String current, String cached) {
        JSONObject a;
        JSONObject b;
        try {
            a = JSON.parseObject(current);
            b = JSON.parseObject(cached);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (a == null || b == null) return false;
        for (String field : List.of("origin", "destination", "date", "days", "people", "budget")) {
            if (!Objects.equals(normalize(a.getString(field)), normalize(b.getString(field)))) return false;
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T withCommands(java.util.function.Function<RedisConnection, T> callback) {
        try (var connection = connectionFactory.getConnection()) {
            return callback.apply(connection);
        }
    }

    private static byte[] floats(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return value == null ? null : new String(value, StandardCharsets.UTF_8); }
    private static String text(Object value) {
        return value == null ? null : value instanceof byte[] bytes ? text(bytes) : value.toString();
    }
}
