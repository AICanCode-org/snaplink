package org.aicancode.snaplink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration from environment variables (12-factor). Same names in every track. */
@Component
public class AppProperties {

    private final String databaseUrl;
    private final String redisUrl;
    private final String publicBaseUrl;
    private final String migrationsDir;
    private final String insightsProvider;
    private final String llmBaseUrl;
    private final String llmApiKey;
    private final String llmModel;
    private final int llmTimeoutMs;

    public AppProperties(
            @Value("${DATABASE_URL:postgresql://snaplink:snaplink@localhost:5432/snaplink}") String databaseUrl,
            @Value("${REDIS_URL:}") String redisUrl,
            @Value("${PUBLIC_BASE_URL:http://localhost:8080}") String publicBaseUrl,
            @Value("${MIGRATIONS_DIR:../../spec/db/migrations}") String migrationsDir,
            @Value("${INSIGHTS_PROVIDER:template}") String insightsProvider,
            @Value("${LLM_BASE_URL:http://localhost:11434/v1}") String llmBaseUrl,
            @Value("${LLM_API_KEY:}") String llmApiKey,
            @Value("${LLM_MODEL:llama3.2:1b}") String llmModel,
            @Value("${LLM_TIMEOUT_MS:5000}") int llmTimeoutMs) {
        this.databaseUrl = databaseUrl;
        this.redisUrl = redisUrl;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.migrationsDir = migrationsDir;
        this.insightsProvider = insightsProvider.toLowerCase();
        this.llmBaseUrl = stripTrailingSlash(llmBaseUrl);
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.llmTimeoutMs = llmTimeoutMs;
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    public String databaseUrl() {
        return databaseUrl;
    }

    public String redisUrl() {
        return redisUrl;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public String migrationsDir() {
        return migrationsDir;
    }

    public boolean llmEnabled() {
        return "llm".equals(insightsProvider);
    }

    public String llmBaseUrl() {
        return llmBaseUrl;
    }

    public String llmApiKey() {
        return llmApiKey;
    }

    public String llmModel() {
        return llmModel;
    }

    public int llmTimeoutMs() {
        return llmTimeoutMs;
    }
}
