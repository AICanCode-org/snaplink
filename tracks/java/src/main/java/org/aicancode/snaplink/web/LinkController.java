package org.aicancode.snaplink.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aicancode.snaplink.config.AppProperties;
import org.aicancode.snaplink.insights.InsightInput;
import org.aicancode.snaplink.insights.InsightResult;
import org.aicancode.snaplink.insights.InsightService;
import org.aicancode.snaplink.link.DailyClicks;
import org.aicancode.snaplink.link.Link;
import org.aicancode.snaplink.link.LinkService;
import org.aicancode.snaplink.link.Validation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer for /api/v1/links. Thin: parse input, call the service, shape the JSON. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService service;
    private final InsightService insights;
    private final String publicBaseUrl;

    public LinkController(LinkService service, InsightService insights, AppProperties props) {
        this.service = service;
        this.insights = insights;
        this.publicBaseUrl = props.publicBaseUrl();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Link link = service.create(body.get("url"), body.get("alias"), body.get("expiresInDays"));
        return ResponseEntity.created(URI.create("/api/v1/links/" + link.code())).body(toJson(link));
    }

    @GetMapping("/{code}")
    public Map<String, Object> get(@PathVariable String code) {
        return toJson(service.get(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/stats")
    public Map<String, Object> stats(@PathVariable String code, @RequestParam(required = false) String days) {
        int window = Validation.validateDays(days);
        Link link = service.get(code);
        List<DailyClicks> daily = service.dailySeries(link, window);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", link.code());
        body.put("totalClicks", link.clickCount());
        body.put("days", daily.size());
        body.put("daily", daily.stream().map(d -> Map.of("date", d.day().toString(), "clicks", d.clicks())).toList());
        return body;
    }

    @GetMapping("/{code}/insights")
    public Map<String, Object> insights(@PathVariable String code) {
        Link link = service.get(code);
        List<DailyClicks> daily = service.dailySeries(link, InsightInput.WINDOW_DAYS);
        InsightResult result = insights.summarise(new InsightInput(link.code(), link.clickCount(), daily));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", link.code());
        body.put("provider", result.provider());
        body.put("fallback", result.fallback());
        body.put("summary", result.summary());
        body.put("generatedAt", Iso.format(service.now()));
        return body;
    }

    private Map<String, Object> toJson(Link link) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", link.code());
        body.put("shortUrl", publicBaseUrl + "/" + link.code());
        body.put("url", link.longUrl());
        body.put("custom", link.custom());
        body.put("createdAt", Iso.format(link.createdAt()));
        body.put("expiresAt", Iso.format(link.expiresAt()));
        body.put("clickCount", link.clickCount());
        return body;
    }
}
