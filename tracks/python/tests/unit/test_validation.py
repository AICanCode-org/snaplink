import pytest

from app.errors import ApiError
from app.validation import validate_alias, validate_days, validate_expires_in_days, validate_url


@pytest.mark.parametrize("url", ["https://www.aicancode.org", "http://example.com/a?b=c#d", "HTTPS://EXAMPLE.COM"])
def test_valid_urls(url):
    assert validate_url(url) == url


@pytest.mark.parametrize(
    "url",
    [
        None,
        "",
        "   ",
        "ftp://example.com",
        "javascript:alert(1)",
        "https://",
        "example.com",
        "https://exa mple.com",
        123,
        "https://a.com/" + "x" * 2048,
    ],
)
def test_invalid_urls(url):
    with pytest.raises(ApiError) as err:
        validate_url(url)
    assert err.value.status == 400


@pytest.mark.parametrize("alias", ["abc", "my-link_2", "A" * 32])
def test_valid_aliases(alias):
    assert validate_alias(alias) == alias


@pytest.mark.parametrize("alias", ["ab", "A" * 33, "has space", "emoji😀", "api", "HEALTH", "Metrics", 42])
def test_invalid_aliases(alias):
    with pytest.raises(ApiError):
        validate_alias(alias)


def test_alias_is_optional():
    assert validate_alias(None) is None


@pytest.mark.parametrize(
    "value,ok",
    [(None, True), (1, True), (365, True), (0, False), (366, False), ("5", False), (2.5, False), (True, False)],
)
def test_expires_in_days(value, ok):
    if ok:
        assert validate_expires_in_days(value) == value
    else:
        with pytest.raises(ApiError):
            validate_expires_in_days(value)


def test_days_defaults_to_seven_and_is_bounded():
    assert validate_days(None) == 7
    assert validate_days("30") == 30
    for bad in ("0", "91", "abc"):
        with pytest.raises(ApiError):
            validate_days(bad)
