"""Input validation rules shared by the contract (spec/openapi.yaml)."""

from __future__ import annotations

import re
from urllib.parse import urlsplit

from .errors import validation_error

MAX_URL_LENGTH = 2048
ALIAS_PATTERN = re.compile(r"^[A-Za-z0-9_-]{3,32}$")
RESERVED_ALIASES = {"api", "health", "metrics", "docs", "admin", "static"}


def validate_url(url: object) -> str:
    if not isinstance(url, str) or not url.strip():
        raise validation_error("url is required")
    if len(url) > MAX_URL_LENGTH:
        raise validation_error(f"url must be at most {MAX_URL_LENGTH} characters")
    if any(ch.isspace() for ch in url):
        raise validation_error("url must not contain whitespace")
    parts = urlsplit(url)
    if parts.scheme.lower() not in ("http", "https"):
        raise validation_error("url must start with http:// or https://")
    if not parts.hostname:
        raise validation_error("url must include a host")
    return url


def validate_alias(alias: object) -> str | None:
    if alias is None:
        return None
    if not isinstance(alias, str) or not ALIAS_PATTERN.match(alias):
        raise validation_error("alias must be 3-32 characters: letters, digits, '-' or '_'")
    if alias.lower() in RESERVED_ALIASES:
        raise validation_error(f"alias '{alias}' is reserved")
    return alias


def validate_expires_in_days(value: object) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= 365:
        raise validation_error("expiresInDays must be an integer between 1 and 365")
    return value


def validate_days(value: object) -> int:
    if value is None:
        return 7
    try:
        days = int(str(value))
    except ValueError:
        raise validation_error("days must be an integer between 1 and 90") from None
    if not 1 <= days <= 90:
        raise validation_error("days must be an integer between 1 and 90")
    return days
