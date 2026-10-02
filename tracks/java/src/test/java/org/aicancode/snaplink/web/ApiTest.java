package org.aicancode.snaplink.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.aicancode.snaplink.config.AppProperties;
import org.aicancode.snaplink.insights.InsightService;
import org.aicancode.snaplink.insights.TemplateInsightGenerator;
import org.aicancode.snaplink.link.LinkService;
import org.aicancode.snaplink.link.ShortCodeGenerator;
import org.aicancode.snaplink.support.InMemoryLinkCache;
import org.aicancode.snaplink.support.InMemoryLinkRepository;
import org.aicancode.snaplink.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * API-level tests with in-memory adapters (no Docker, no Spring context). They mirror
 * spec/openapi.yaml; the same scenarios exist in the Python and TypeScript tracks.
 */
class ApiTest {

    private MockMvc mvc;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        AppProperties props = new AppProperties("postgresql://unused/db", "", "http://localhost:8080", "unused",
                "template", "http://unused", "", "m", 1000);
        LinkService service = new LinkService(new InMemoryLinkRepository(), new InMemoryLinkCache(), clock,
                new ShortCodeGenerator());
        InsightService insights = new InsightService(new TemplateInsightGenerator(), null);
        mvc = MockMvcBuilders.standaloneSetup(new LinkController(service, insights, props),
                        new RedirectController(service), new HealthController(service))
                .setControllerAdvice(new ProblemHandler())
                .build();
    }

    private String create(String json) throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsAndReadsLink() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.aicancode.org\",\"alias\":\"acc\",\"expiresInDays\":7}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/links/acc"))
                .andExpect(jsonPath("$.code").value("acc"))
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/acc"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-02T09:30:00.000Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-09T09:30:00.000Z"));
        mvc.perform(get("/api/v1/links/acc")).andExpect(jsonPath("$.url").value("https://www.aicancode.org"));
    }

    @Test
    void validationErrorsAreProblemJson() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"ftp://nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://snaplink.dev/problems/validation-error"));
    }

    @Test
    void nonObjectBodiesAre400() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("[\"https://a.com\"]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aliasConflictIs409() throws Exception {
        create("{\"url\":\"https://a.com\",\"alias\":\"dup\"}");
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://b.com\",\"alias\":\"dup\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void redirectCountsClicks() throws Exception {
        String code = create("{\"url\":\"https://www.aicancode.org\"}");
        mvc.perform(get("/" + code)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.aicancode.org"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/" + code));
        mvc.perform(get("/api/v1/links/" + code)).andExpect(jsonPath("$.clickCount").value(2));
    }

    @Test
    void unknownCodeIs404() throws Exception {
        mvc.perform(get("/doesnotexist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://snaplink.dev/problems/not-found"));
    }

    @Test
    void expiredLinkIs410() throws Exception {
        String code = create("{\"url\":\"https://a.com\",\"expiresInDays\":1}");
        clock.advance(Duration.ofDays(2));
        mvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    void statsAndInsights() throws Exception {
        String code = create("{\"url\":\"https://a.com\",\"alias\":\"stats1\"}");
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/" + code));
        }
        mvc.perform(get("/api/v1/links/" + code + "/stats").param("days", "3"))
                .andExpect(content().json("{\"code\":\"stats1\",\"totalClicks\":3,\"days\":3,\"daily\":["
                        + "{\"date\":\"2026-09-30\",\"clicks\":0},{\"date\":\"2026-10-01\",\"clicks\":0},"
                        + "{\"date\":\"2026-10-02\",\"clicks\":3}]}"));
        mvc.perform(get("/api/v1/links/" + code + "/stats").param("days", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/links/" + code + "/insights"))
                .andExpect(jsonPath("$.provider").value("template"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.summary", startsWith("/stats1 got 3 clicks in the last 7 days")));
    }

    @Test
    void deletesLinks() throws Exception {
        String code = create("{\"url\":\"https://a.com\"}");
        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());
        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNotFound());
    }
}
