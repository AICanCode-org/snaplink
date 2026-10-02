package org.aicancode.snaplink.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.aicancode.snaplink.cache.RedisLinkCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration tests: real PostgreSQL + Redis (docker compose up -d postgres redis).
 * Run with: mvn verify -Pintegration. Uses TEST_DATABASE_URL / TEST_REDIS_URL (never your dev DB).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    static final String DB_URL = env("TEST_DATABASE_URL", "postgresql://snaplink:snaplink@localhost:5432/snaplink_test");
    static final String REDIS_URL = env("TEST_REDIS_URL", "redis://localhost:6379/1");

    static RedisClient redisClient;
    static StatefulRedisConnection<String, String> redis;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", () -> DB_URL);
        registry.add("REDIS_URL", () -> REDIS_URL);
        registry.add("INSIGHTS_PROVIDER", () -> "template");
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcClient jdbcClient;

    private final HttpClient http = HttpClient.newHttpClient(); // never follows redirects by default

    @BeforeAll
    static void connectRedis() {
        redisClient = RedisClient.create(REDIS_URL);
        redis = redisClient.connect();
    }

    @AfterAll
    static void closeRedis() {
        redis.close();
        redisClient.shutdown();
    }

    @BeforeEach
    void clean() {
        jdbcClient.sql("TRUNCATE links RESTART IDENTITY CASCADE").update();
        redis.sync().flushdb();
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String createCode(String json) throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/links", json);
        assertThat(r.statusCode()).isEqualTo(201);
        return r.body().replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void reportsHealthyDependencies() throws Exception {
        assertThat(send("GET", "/health", null).body()).contains("\"database\":\"UP\"").contains("\"cache\":\"UP\"");
    }

    @Test
    void recordsAppliedMigrations() {
        assertThat(jdbcClient.sql("SELECT version FROM schema_migrations ORDER BY version").query(Integer.class).list())
                .startsWith(1, 2);
    }

    @Test
    void persistsClicksAcrossTheFullFlow() throws Exception {
        String code = createCode("{\"url\":\"https://www.aicancode.org\"}");
        for (int i = 0; i < 3; i++) {
            assertThat(send("GET", "/" + code, null).statusCode()).isEqualTo(302);
        }
        assertThat(send("GET", "/api/v1/links/" + code, null).body()).contains("\"clickCount\":3");
        assertThat(send("GET", "/api/v1/links/" + code, null).body()).doesNotContain("\"lastAccessedAt\":null");
        assertThat(send("GET", "/api/v1/links/" + code + "/stats", null).body()).contains("\"totalClicks\":3");
        assertThat(redis.sync().exists(RedisLinkCache.KEY_PREFIX + code)).isEqualTo(1L);
    }

    @Test
    void enforcesUniqueAliasesInTheDatabase() throws Exception {
        createCode("{\"url\":\"https://a.com\",\"alias\":\"uniq\"}");
        assertThat(send("POST", "/api/v1/links", "{\"url\":\"https://b.com\",\"alias\":\"uniq\"}").statusCode())
                .isEqualTo(409);
    }

    @Test
    void returns410WhenTheDatabaseSaysExpired() throws Exception {
        String code = createCode("{\"url\":\"https://a.com\",\"expiresInDays\":5}");
        jdbcClient.sql("UPDATE links SET expires_at = now() - interval '1 minute' WHERE code = ?").param(code).update();
        assertThat(send("GET", "/" + code, null).statusCode()).isEqualTo(410);
    }

    @Test
    void cascadesDeletesToAnalyticsAndCache() throws Exception {
        String code = createCode("{\"url\":\"https://a.com\"}");
        send("GET", "/" + code, null);
        assertThat(send("DELETE", "/api/v1/links/" + code, null).statusCode()).isEqualTo(204);
        assertThat(jdbcClient.sql("SELECT count(*) FROM click_daily").query(Long.class).single()).isZero();
        assertThat(redis.sync().exists(RedisLinkCache.KEY_PREFIX + code)).isZero();
        assertThat(send("GET", "/" + code, null).statusCode()).isEqualTo(404);
    }

    @Test
    void redisDownDegradesGracefully() {
        try (RedisLinkCache dead = new RedisLinkCache("redis://localhost:6390/0")) {
            assertThat(dead.status()).isEqualTo("DOWN");
            assertThat(dead.get("anything")).isEmpty();
        }
    }
}
