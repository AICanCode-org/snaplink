package org.aicancode.snaplink.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.aicancode.snaplink.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://www.aicancode.org", "http://example.com/a?b=c#d", "HTTPS://EXAMPLE.COM"})
    void acceptsValidUrls(String url) {
        assertThat(Validation.validateUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ftp://example.com", "javascript:alert(1)", "https://", "example.com",
        "https://exa mple.com"})
    void rejectsInvalidUrls(String url) {
        assertThatThrownBy(() -> Validation.validateUrl(url)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsNonStringAndTooLongUrls() {
        assertThatThrownBy(() -> Validation.validateUrl(123)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Validation.validateUrl("https://a.com/" + "x".repeat(2048)))
                .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "my-link_2", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void acceptsValidAliases(String alias) {
        assertThat(Validation.validateAlias(alias)).isEqualTo(alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "has space", "emoji😀", "api", "HEALTH", "Metrics"})
    void rejectsInvalidAliases(String alias) {
        assertThatThrownBy(() -> Validation.validateAlias(alias)).isInstanceOf(ApiException.class);
    }

    @Test
    void aliasIsOptional() {
        assertThat(Validation.validateAlias(null)).isNull();
        assertThatThrownBy(() -> Validation.validateAlias(42)).isInstanceOf(ApiException.class);
    }

    @Test
    void expiresInDaysBounds() {
        assertThat(Validation.validateExpiresInDays(null)).isNull();
        assertThat(Validation.validateExpiresInDays(1)).isEqualTo(1);
        assertThat(Validation.validateExpiresInDays(365)).isEqualTo(365);
        for (Object bad : new Object[] {0, 366, "5", 2.5, true}) {
            assertThatThrownBy(() -> Validation.validateExpiresInDays(bad)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void daysDefaultsToSevenAndIsBounded() {
        assertThat(Validation.validateDays(null)).isEqualTo(7);
        assertThat(Validation.validateDays("30")).isEqualTo(30);
        for (String bad : new String[] {"0", "91", "abc"}) {
            assertThatThrownBy(() -> Validation.validateDays(bad)).isInstanceOf(ApiException.class);
        }
    }
}
