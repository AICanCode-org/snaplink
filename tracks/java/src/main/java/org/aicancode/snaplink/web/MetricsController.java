package org.aicancode.snaplink.web;

import org.aicancode.snaplink.link.LinkService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /metrics in Prometheus text format (literal paths win over RedirectController's /{code}). */
@RestController
public class MetricsController {

    private static final MediaType PROMETHEUS_TEXT = MediaType.parseMediaType("text/plain;version=0.0.4;charset=utf-8");

    private final LinkService service;

    public MetricsController(LinkService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ResponseEntity<String> metrics() {
        return ResponseEntity.ok().contentType(PROMETHEUS_TEXT).body(service.metrics().render());
    }
}
