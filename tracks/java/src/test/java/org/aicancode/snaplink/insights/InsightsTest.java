package org.aicancode.snaplink.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.aicancode.snaplink.link.DailyClicks;
import org.junit.jupiter.api.Test;

class InsightsTest {

    private final TemplateInsightGenerator template = new TemplateInsightGenerator();

    private static List<DailyClicks> week(long... clicks) {
        List<DailyClicks> days = new ArrayList<>();
        LocalDate start = LocalDate.parse("2026-09-26");
        for (int i = 0; i < clicks.length; i++) {
            days.add(new DailyClicks(start.plusDays(i), clicks[i]));
        }
        return days;
    }

    @Test
    void templateNoClicks() {
        assertThat(template.generate(new InsightInput("abc1234", 5, week(0, 0, 0, 0, 0, 0, 0)))).isEqualTo(
                "/abc1234 had no clicks in the last 7 days (all-time: 5). Share it where your audience hangs out!");
    }

    @Test
    void templateTrendUpAndBusiestDay() {
        assertThat(template.generate(new InsightInput("abc1234", 20, week(0, 1, 0, 2, 3, 3, 1)))).isEqualTo(
                "/abc1234 got 10 clicks in the last 7 days (all-time: 20). Busiest day: 2026-09-30 "
                        + "with 3 clicks. Trend: up compared with earlier in the week.");
    }

    @Test
    void templateSingularAndSteady() {
        String text = template.generate(new InsightInput("x1y", 1, week(0, 0, 0, 1, 0, 0, 0)));
        assertThat(text).contains("got 1 click in", "with 1 click.", "Trend: steady");
    }

    @Test
    void templateTrendDown() {
        String text = template.generate(new InsightInput("x1y", 9, week(5, 2, 1, 0, 0, 1, 0)));
        assertThat(text).contains("Busiest day: 2026-09-26 with 5 clicks", "Trend: down");
    }

    private final InsightInput empty = new InsightInput("c0de", 0, week(0, 0, 0, 0, 0, 0, 0));

    @Test
    void serviceUsesTemplateByDefault() {
        assertThat(new InsightService(template, null).summarise(empty))
                .extracting(InsightResult::provider, InsightResult::fallback).containsExactly("template", false);
    }

    @Test
    void serviceUsesLlmWhenConfigured() {
        assertThat(new InsightService(template, d -> "Great week!").summarise(empty))
                .isEqualTo(new InsightResult("llm", false, "Great week!"));
    }

    @Test
    void serviceFallsBackWhenLlmFails() {
        InsightResult result = new InsightService(template, d -> {
            throw new java.util.concurrent.TimeoutException("LLM took too long");
        }).summarise(empty);
        assertThat(result.provider()).isEqualTo("template");
        assertThat(result.fallback()).isTrue();
        assertThat(result.summary()).startsWith("/c0de had no clicks");
    }

    @Test
    void llmGeneratorCallsOpenAiCompatibleEndpoint() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"choices\":[{\"message\":{\"content\":\"  Clicks are rising.  \"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            LlmInsightGenerator gen = new LlmInsightGenerator(base, "test-model", "secret-key", 2000);
            assertThat(gen.generate(new InsightInput("c0de", 3, week(0, 0, 0, 1, 1, 1, 0))))
                    .isEqualTo("Clicks are rising.");
            assertThat(path.get()).isEqualTo("/v1/chat/completions");
            assertThat(auth.get()).isEqualTo("Bearer secret-key");
            assertThat(body.get()).contains("\"model\":\"test-model\"").contains("c0de");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void llmGeneratorThrowsOnHttpError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            LlmInsightGenerator gen = new LlmInsightGenerator(base, "m", "", 2000);
            assertThatThrownBy(() -> gen.generate(empty)).hasMessageContaining("LLM HTTP 500");
        } finally {
            server.stop(0);
        }
    }
}
