package org.aicancode.snaplink.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.aicancode.snaplink.web.ApiException;

/** Input validation rules shared by the contract (spec/openapi.yaml). */
public final class Validation {

    public static final int MAX_URL_LENGTH = 2048;
    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    public static final Set<String> RESERVED_ALIASES = Set.of("api", "health", "metrics", "docs", "admin", "static");

    private Validation() {
    }

    public static String validateUrl(Object value) {
        if (!(value instanceof String url) || url.isBlank()) {
            throw ApiException.validation("url is required");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw ApiException.validation("url must be at most " + MAX_URL_LENGTH + " characters");
        }
        if (WHITESPACE.matcher(url).find()) {
            throw ApiException.validation("url must not contain whitespace");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw ApiException.validation("url must start with http:// or https://");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ApiException.validation("url must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw ApiException.validation("url must include a host");
        }
        return url;
    }

    public static String validateAlias(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String alias) || !ALIAS.matcher(alias).matches()) {
            throw ApiException.validation("alias must be 3-32 characters: letters, digits, '-' or '_'");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("alias '" + alias + "' is reserved");
        }
        return alias;
    }

    public static Integer validateExpiresInDays(Object value) {
        if (value == null) {
            return null;
        }
        if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 1
                && ((Number) value).longValue() <= 365) {
            return ((Number) value).intValue();
        }
        throw ApiException.validation("expiresInDays must be an integer between 1 and 365");
    }

    public static int validateDays(String value) {
        if (value == null) {
            return 7;
        }
        if (value.matches("^\\d{1,3}$")) {
            int days = Integer.parseInt(value);
            if (days >= 1 && days <= 90) {
                return days;
            }
        }
        throw ApiException.validation("days must be an integer between 1 and 90");
    }
}
