package org.aicancode.snaplink.web;

import java.util.Map;
import org.aicancode.snaplink.link.LinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness + dependency health. 503 only when the database is down; a missing cache is "degraded". */
@RestController
public class HealthController {

    private final LinkService service;

    public HealthController(LinkService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = service.repository().ping();
        String db = dbUp ? "UP" : "DOWN";
        Map<String, Object> body = Map.of("status", db, "checks", Map.of("database", db, "cache", service.cache().status()));
        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }
}
