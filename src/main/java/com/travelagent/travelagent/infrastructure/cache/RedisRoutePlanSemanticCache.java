package com.travelagent.travelagent.infrastructure.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.application.planning.port.out.RoutePlanSemanticCache;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.CreateArgs;
import io.lettuce.core.search.arguments.FieldArgs;
import io.lettuce.core.search.arguments.NumericFieldArgs;
import io.lettuce.core.search.arguments.SearchArgs;
import io.lettuce.core.search.arguments.TagFieldArgs;
import io.lettuce.core.search.arguments.VectorFieldArgs;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Redis Stack vector cache. TTL is applied to every route entry. */
@Component
public class RedisRoutePlanSemanticCache implements RoutePlanSemanticCache {
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
    private volatile int dimensions;

    public RedisRoutePlanSemanticCache(RedisConnectionFactory connectionFactory,
                                       EmbeddingModel embeddingModel,
                                       @Value("${travel-agent.route-cache.ttl:PT24H}") Duration ttl,
                                       @Value("${travel-agent.route-cache.similarity-threshold:0.92}") double threshold) {
        this.connectionFactory = connectionFactory;
        this.embeddingModel = embeddingModel;
        this.ttl = ttl;
        this.threshold = threshold;
    }

    @Override
    public Optional<Hit> find(String requirements) {
        byte[] query = vector(requirements);
        return withCommands(redis -> {
            ensureIndex(redis, query.length / Float.BYTES);
            SearchArgs<byte[], byte[]> args = SearchArgs.<byte[], byte[]>builder()
                    .withScores().limit(0, 1).returnField(bytes(PLAN)).returnField(bytes(REQUIREMENTS))
                    .param(bytes("query_vector"), query).dialect(io.lettuce.core.search.arguments.QueryDialects.DIALECT2).build();
            SearchReply<byte[], byte[]> reply = redis.ftSearch(bytes(INDEX), bytes("*=>[KNN 1 @vector $query_vector AS score]"), args);
            if (reply.isEmpty()) return Optional.empty();
            SearchReply.SearchResult<byte[], byte[]> result = reply.getResults().getFirst();
            // RediSearch returns cosine distance (0 = identical), not similarity.
            double similarity = 1d - (result.getScore() == null ? 1d : result.getScore());
            if (similarity < threshold) return Optional.empty();
            String cachedRequirements = text(field(result, REQUIREMENTS));
            if (!matchesMandatoryRequirements(requirements, cachedRequirements)) return Optional.empty();
            String plan = text(field(result, PLAN));
            return plan == null ? Optional.empty() : Optional.of(new Hit(plan, similarity));
        });
    }

    @Override
    public void put(String requirements, String routePlan) {
        byte[] query = vector(requirements);
        withCommands(redis -> {
            ensureIndex(redis, query.length / Float.BYTES);
            byte[] key = bytes(new String(PREFIX, StandardCharsets.UTF_8) + sha256(canonical(requirements)));
            redis.hset(key, Map.of(bytes(VECTOR), query, bytes(PLAN), bytes(routePlan), bytes(REQUIREMENTS), bytes(requirements), bytes(EXPIRES_AT), bytes(Long.toString(System.currentTimeMillis() + ttl.toMillis()))));
            redis.expire(key, ttl);
            return null;
        });
    }

    private byte[] vector(String requirements) {
        String text = canonical(requirements);
        return floats(embeddingModel.embed(text));
    }

    private String canonical(String requirements) {
        JSONObject value = JSON.parseObject(requirements);
        return value == null ? requirements : JSON.toJSONString(value);
    }

    private void ensureIndex(RedisCommands<byte[], byte[]> redis, int dimensions) {
        if (this.dimensions == dimensions) return;
        synchronized (this) {
            if (this.dimensions == dimensions) return;
            List<FieldArgs<byte[]>> fields = List.of(
                    VectorFieldArgs.<byte[]>builder().hnsw().type(VectorFieldArgs.VectorType.FLOAT32)
                            .dimensions(dimensions).distanceMetric(VectorFieldArgs.DistanceMetric.COSINE)
                            .attribute("M", 16).attribute("EF_CONSTRUCTION", 200).as(bytes(VECTOR)).build(),
                    TagFieldArgs.<byte[]>builder().as(bytes(REQUIREMENTS)).build(),
                    NumericFieldArgs.<byte[]>builder().as(bytes(EXPIRES_AT)).build());
            try {
                redis.ftCreate(bytes(INDEX), CreateArgs.<byte[], byte[]>builder().on(CreateArgs.TargetType.HASH).withPrefix(PREFIX).build(), fields);
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

    private byte[] field(SearchReply.SearchResult<byte[], byte[]> result, String name) {
        byte[] expected = bytes(name);
        return result.getFields().entrySet().stream()
                .filter(entry -> Arrays.equals(entry.getKey(), expected))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
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

    @SuppressWarnings("unchecked")
    private <T> T withCommands(Function<RedisCommands<byte[], byte[]>, T> callback) {
        try (var connection = connectionFactory.getConnection()) {
            Object nativeConnection = connection.getNativeConnection();
            return callback.apply(((StatefulRedisConnection<byte[], byte[]>) nativeConnection).sync());
        }
    }

    private static byte[] floats(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return value == null ? null : new String(value, StandardCharsets.UTF_8); }
}
