package org.acme.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

public class RedisExecutor {

    private final Supplier<UnifiedJedis> unifiedJedisSupplier;
    private final ObjectMapper objectMapper;
    private final String redisHosts;

    public RedisExecutor(@ConfigProperty(name = "quarkus.redis.hosts", defaultValue = "redis://localhost:6379")
                         String redisHosts,
                         ObjectMapper objectMapper) {

        this.redisHosts = redisHosts;
        this.unifiedJedisSupplier = () -> new UnifiedJedis(redisHosts);
        this.objectMapper = objectMapper;
    }

    @Produces
    public Supplier<UnifiedJedis> unifiedJedis() {
        return this.unifiedJedisSupplier;
    }

    public <T> T retrieve(Function<RedisContext, T> function) {
        try (var jedis = unifiedJedisSupplier.get()) {
            return function.apply(DefaultRedisContext.of(jedis, objectMapper));
        }
    }

    public void execute(Consumer<RedisContext> consumer) {
        try (var jedis = unifiedJedisSupplier.get()) {
            consumer.accept(DefaultRedisContext.of(jedis, objectMapper));
        }
    }

    public sealed interface RedisContext permits DefaultRedisContext {

        UnifiedJedis jedis();

        ObjectMapper objectMapper();

        default String encodeToJSON(Object object) {
            if (object == null)
                return null;
            try {
                return objectMapper().writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        default <T> Function<String, T> converterFor(Class<T> type) {
            return (String json) -> decodeFromJSON(json, type);
        }

        default <T> Function<String, T> converterFor(TypeReference<T> typeReference) {
            return (String json) -> decodeFromJSON(json, typeReference);
        }

        default <T> T decodeFromJSON(String json, TypeReference<T> typeReference) {
            Objects.requireNonNull(typeReference, "typeReference is required");
            return ofNullable(json)
                    .map(data -> {
                        try {
                            return objectMapper().readValue(data, typeReference);
                        } catch (JsonMappingException e) {
                            throw new RuntimeException(e);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }).orElse(null);
        }

        default <T> T decodeFromJSON(String json, Class<T> clazz) {
            Objects.requireNonNull(clazz, "class is required");
            return ofNullable(json)
                    .map(data -> {
                        try {
                            return objectMapper().readValue(json, clazz);
                        } catch (JsonMappingException e) {
                            throw new RuntimeException(e);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }).orElse(null);
        }

    }

    record DefaultRedisContext(UnifiedJedis jedis, ObjectMapper objectMapper) implements RedisContext {
        static DefaultRedisContext of(UnifiedJedis jedis, ObjectMapper objectMapper) {
            return new DefaultRedisContext(jedis, objectMapper);
        }
    }
}
