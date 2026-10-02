package org.aicancode.snaplink.link;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Input validation rules shared by the contract (spec/openapi.yaml).
 * Every method throws {@code ApiException.validation("...")} (HTTP 400) for bad input.
 * The exact rules are in spec/requirements.md (FR-1..FR-3) and ValidationTest.
 */
public final class Validation {

    public static final int MAX_URL_LENGTH = 2048;
    public static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    public static final Set<String> RESERVED_ALIASES = Set.of("api", "health", "metrics", "docs", "admin", "static");

    private Validation() {
    }

    public static String validateUrl(Object value) {
        // TODO(phase-3 M1): non-blank String, <= MAX_URL_LENGTH, no whitespace, scheme http/https only
        // (java.net.URI), must have a host. Return the url unchanged.
        throw new UnsupportedOperationException("TODO(phase-3 M1): Validation.validateUrl");
    }

    public static String validateAlias(Object value) {
        // TODO(phase-3 M1): null -> null; must match ALIAS; reject RESERVED_ALIASES (case-insensitive).
        throw new UnsupportedOperationException("TODO(phase-3 M1): Validation.validateAlias");
    }

    public static Integer validateExpiresInDays(Object value) {
        // TODO(phase-3 M1): null -> null; an Integer/Long (JSON number without decimals) between 1 and 365.
        throw new UnsupportedOperationException("TODO(phase-3 M1): Validation.validateExpiresInDays");
    }

    public static int validateDays(String value) {
        // TODO(phase-3 M4): null -> 7; otherwise digits only (it arrives as a query string), 1..90.
        throw new UnsupportedOperationException("TODO(phase-3 M4): Validation.validateDays");
    }
}
