package org.aicancode.snaplink.cache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aicancode.snaplink.link.CachedLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/** Redis adapter (Lettuce). Every failure degrades to "cache miss" so redirects keep working. */
public class RedisLinkCache implements LinkCache, AutoCloseable {

    public static final String KEY_PREFIX = "snaplink:link:";
    private static final Logger LOG = LoggerFactory.getLogger(RedisLinkCache.class);
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final long RECONNECT_BACKOFF_MS = 5000;

    private final RedisClient client;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile long lastAttempt;

    public RedisLinkCache(String url) {
        RedisURI uri = RedisURI.create(url);
        uri.setTimeout(TIMEOUT);
        this.client = RedisClient.create(uri);
        this.client.setOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    private synchronized RedisCommands<String, String> commands() {
        if (connection == null) {
            long now = System.currentTimeMillis();
            if (now - lastAttempt < RECONNECT_BACKOFF_MS && lastAttempt != 0) {
                throw new IllegalStateException("redis unavailable (backing off)");
            }
            lastAttempt = now;
            connection = client.connect(); // Lettuce auto-reconnects after the first success
            connection.setTimeout(TIMEOUT);
        }
        return connection.sync();
    }

    @Override
    public Optional<CachedLink> get(String code) {
        try {
            String raw = commands().get(KEY_PREFIX + code);
            if (raw == null) {
                return Optional.empty();
            }
            Map<?, ?> data = json.readValue(raw, Map.class);
            Object expires = data.get("expiresAt");
            return Optional.of(new CachedLink(((Number) data.get("id")).longValue(), (String) data.get("url"),
                    expires == null ? null : Instant.parse((String) expires)));
        } catch (RuntimeException e) {
            LOG.warn("cache get failed, falling back to database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String code, CachedLink link, long ttlSeconds) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", link.id());
        data.put("url", link.longUrl());
        data.put("expiresAt", link.expiresAt() == null ? null : link.expiresAt().toString());
        try {
            commands().set(KEY_PREFIX + code, json.writeValueAsString(data), SetArgs.Builder.ex(ttlSeconds));
        } catch (RuntimeException e) {
            LOG.warn("cache put failed: {}", e.getMessage());
        }
    }

    @Override
    public void delete(String code) {
        try {
            commands().del(KEY_PREFIX + code);
        } catch (RuntimeException e) {
            LOG.warn("cache delete failed: {}", e.getMessage());
        }
    }

    @Override
    public String status() {
        try {
            return "PONG".equals(commands().ping()) ? "UP" : "DOWN";
        } catch (RuntimeException e) {
            return "DOWN";
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        client.shutdown();
    }
}
