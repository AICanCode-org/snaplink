package org.aicancode.snaplink.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.aicancode.snaplink.config.AppProperties;
import org.aicancode.snaplink.insights.InsightService;
import org.aicancode.snaplink.insights.TemplateInsightGenerator;
import org.aicancode.snaplink.link.LinkService;
import org.aicancode.snaplink.link.ShortCodeGenerator;
import org.aicancode.snaplink.metrics.Metrics;
import org.aicancode.snaplink.support.InMemoryLinkCache;
import org.aicancode.snaplink.support.InMemoryLinkRepository;
import org.aicancode.snaplink.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Phase 7: request IDs, /metrics, lastAccessedAt (v1.1.0). */
class ObservabilityTest {

    private MockMvc mvc;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        AppProperties props = new AppProperties("postgresql://unused/db", "", "http://localhost:8080", "unused",
                "template", "http://unused", "", "m", 1000);
        LinkService service = new LinkService(new InMemoryLinkRepository(), new InMemoryLinkCache(), clock,
                new ShortCodeGenerator());
        mvc = MockMvcBuilders.standaloneSetup(
                        new LinkController(service, new InsightService(new TemplateInsightGenerator(), null), props),
                        new RedirectController(service), new HealthController(service), new MetricsController(service))
                .setControllerAdvice(new ProblemHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    private String createCode() throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://a.com\"}"))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
    }

    private long metric(String name) throws Exception {
        String text = mvc.perform(get("/metrics")).andReturn().getResponse().getContentAsString();
        return text.lines().filter(l -> l.startsWith(name + " ")).map(l -> Long.parseLong(l.split(" ")[1]))
                .findFirst().orElseThrow(() -> new AssertionError(name + " not exported"));
    }

    @Test
    void generatesAndEchoesRequestIds() throws Exception {
        String generated = mvc.perform(get("/health")).andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(generated).hasSize(36);
        mvc.perform(get("/health").header("X-Request-ID", "abc-123"))
                .andExpect(header().string("X-Request-ID", "abc-123"));
    }

    @Test
    void replacesUnsafeRequestIds() {
        assertThat(RequestIdFilter.incomingOrNewId("ok.id_1-2")).isEqualTo("ok.id_1-2");
        assertThat(RequestIdFilter.incomingOrNewId("bad id\nInjected: yes")).isNotEqualTo("bad id\nInjected: yes");
        assertThat(RequestIdFilter.incomingOrNewId("x".repeat(65))).isNotEqualTo("x".repeat(65));
        assertThat(RequestIdFilter.incomingOrNewId(null)).hasSize(36);
    }

    @Test
    void exportsCountersOnMetrics() throws Exception {
        String text = mvc.perform(get("/metrics")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/plain")))
                .andReturn().getResponse().getContentAsString();
        assertThat(text).contains("# TYPE snaplink_redirects_total counter");
        String code = createCode();
        mvc.perform(get("/" + code)); // miss -> DB, then cached
        mvc.perform(get("/" + code)); // hit
        assertThat(metric(Metrics.LINKS_CREATED)).isEqualTo(1);
        assertThat(metric(Metrics.REDIRECTS)).isEqualTo(2);
        assertThat(metric(Metrics.CACHE_MISSES)).isEqualTo(1);
        assertThat(metric(Metrics.CACHE_HITS)).isEqualTo(1);
        assertThat(metric(Metrics.INSIGHTS_FALLBACK)).isZero();
    }

    @Test
    void tracksLastAccessedAt() throws Exception {
        String code = createCode();
        mvc.perform(get("/api/v1/links/" + code)).andExpect(jsonPath("$.lastAccessedAt").isEmpty());
        clock.advance(Duration.ofHours(1));
        mvc.perform(get("/" + code));
        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(jsonPath("$.lastAccessedAt").value("2026-10-02T10:30:00.000Z"));
    }

    @Test
    void rendersPrometheusTextFormat() {
        Metrics m = new Metrics();
        m.inc(Metrics.REDIRECTS, 3);
        assertThat(m.render()).contains("snaplink_redirects_total 3\n").endsWith("\n");
    }
}
