package org.aicancode.snaplink.cache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.Optional;
import org.aicancode.snaplink.link.CachedLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Redis adapter (Lettuce). Every failure degrades to "cache miss" so redirects keep working. */
public class RedisLinkCache implements LinkCache, AutoCloseable {

    public static final String KEY_PREFIX = "snaplink:link:";
    static final Logger LOG = LoggerFactory.getLogger(RedisLinkCache.class);
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final long RECONNECT_BACKOFF_MS = 5000;

    private final RedisClient client;
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

    synchronized RedisCommands<String, String> commands() {
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
        // TODO(phase-3 M3): commands().get(KEY_PREFIX + code), JSON-decode {id, url, expiresAt} (json.readValue)
        // into a CachedLink. Any RuntimeException -> LOG.warn and Optional.empty() (the DB is the source of truth).
        throw new UnsupportedOperationException("TODO(phase-3 M3): RedisLinkCache.get");
    }

    @Override
    public void put(String code, CachedLink link, long ttlSeconds) {
        // TODO(phase-3 M3): commands().set(KEY_PREFIX + code, json, SetArgs.Builder.ex(ttlSeconds)); swallow + warn.
        throw new UnsupportedOperationException("TODO(phase-3 M3): RedisLinkCache.put");
    }

    @Override
    public void delete(String code) {
        // TODO(phase-3 M3): commands().del(KEY_PREFIX + code); swallow + warn on RuntimeException.
        throw new UnsupportedOperationException("TODO(phase-3 M3): RedisLinkCache.delete");
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
