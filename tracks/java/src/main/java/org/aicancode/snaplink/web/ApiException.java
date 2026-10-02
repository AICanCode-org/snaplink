package org.aicancode.snaplink.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** Domain errors that map 1:1 to RFC 9457 problem responses. */
public class ApiException extends RuntimeException {

    public static final String PROBLEM_BASE = "https://snaplink.dev/problems/";

    private final int status;
    private final String slug;
    private final String title;
    private final String detail;

    public ApiException(int status, String slug, String title, String detail) {
        super(detail == null ? title : detail);
        this.status = status;
        this.slug = slug;
        this.title = title;
        this.detail = detail;
    }

    public static ApiException validation(String detail) {
        return new ApiException(400, "validation-error", "Validation failed", detail);
    }

    public static ApiException notFound(String code) {
        return new ApiException(404, "not-found", "Link not found", "No link with code '" + code + "'");
    }

    public static ApiException expired(String code) {
        return new ApiException(410, "expired", "Link expired", "Link '" + code + "' has expired");
    }

    public static ApiException aliasTaken(String alias) {
        return new ApiException(409, "alias-taken", "Alias already taken", "The alias '" + alias + "' is already in use");
    }

    public int status() {
        return status;
    }

    public Map<String, Object> toProblem() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_BASE + slug);
        body.put("title", title);
        body.put("status", status);
        if (detail != null) {
            body.put("detail", detail);
        }
        return body;
    }
}
