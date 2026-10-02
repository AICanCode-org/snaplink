package org.aicancode.snaplink.config;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Clock;
import javax.sql.DataSource;
import org.aicancode.snaplink.cache.LinkCache;
import org.aicancode.snaplink.cache.NoopLinkCache;
import org.aicancode.snaplink.cache.RedisLinkCache;
import org.aicancode.snaplink.db.DatabaseUrl;
import org.aicancode.snaplink.db.Migrator;
import org.aicancode.snaplink.insights.InsightService;
import org.aicancode.snaplink.insights.LlmInsightGenerator;
import org.aicancode.snaplink.insights.TemplateInsightGenerator;
import org.aicancode.snaplink.link.JdbcLinkRepository;
import org.aicancode.snaplink.link.LinkRepository;
import org.aicancode.snaplink.link.LinkService;
import org.aicancode.snaplink.link.ShortCodeGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires real adapters. Tests swap them for in-memory ones. */
@Configuration
public class AppConfig {

    @Bean
    public DataSource dataSource(AppProperties props) {
        DatabaseUrl url = DatabaseUrl.parse(props.databaseUrl());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url.jdbcUrl());
        ds.setUsername(url.username());
        ds.setPassword(url.password());
        ds.setMaximumPoolSize(10);
        ds.setConnectionTimeout(5000);
        return ds;
    }

    @Bean
    public Migrator migrator(DataSource dataSource, AppProperties props) {
        Migrator migrator = new Migrator(dataSource, Path.of(props.migrationsDir()));
        migrator.migrate(); // fail fast: the app must not serve traffic against an old schema
        return migrator;
    }

    @Bean
    public LinkRepository linkRepository(DataSource dataSource, Migrator migrator) {
        return new JdbcLinkRepository(JdbcClient.create(dataSource));
    }

    @Bean(destroyMethod = "")
    public LinkCache linkCache(AppProperties props) {
        return props.redisUrl().isBlank() ? new NoopLinkCache() : new RedisLinkCache(props.redisUrl());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public LinkService linkService(LinkRepository repo, LinkCache cache, Clock clock) {
        return new LinkService(repo, cache, clock, new ShortCodeGenerator());
    }

    @Bean
    public InsightService insightService(AppProperties props) {
        LlmInsightGenerator llm = props.llmEnabled()
                ? new LlmInsightGenerator(props.llmBaseUrl(), props.llmModel(), props.llmApiKey(), props.llmTimeoutMs())
                : null;
        return new InsightService(new TemplateInsightGenerator(), llm);
    }
}
