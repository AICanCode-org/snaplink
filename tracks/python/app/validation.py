"""Input validation rules shared by the contract (spec/openapi.yaml)."""

from __future__ import annotations

import re

MAX_URL_LENGTH = 2048
ALIAS_PATTERN = re.compile(r"^[A-Za-z0-9_-]{3,32}$")
RESERVED_ALIASES = {"api", "health", "metrics", "docs", "admin", "static"}

# Every function raises errors.validation_error("...") (HTTP 400) for bad input.
# The exact rules are in spec/requirements.md (FR-1..FR-3) and tests/unit/test_validation.py.


def validate_url(url: object) -> str:
    # TODO(phase-3 M1): non-empty string, <= MAX_URL_LENGTH, no whitespace,
    # scheme http/https only (urllib.parse.urlsplit), must have a host. Return the url unchanged.
    raise NotImplementedError("TODO(phase-3 M1): validate_url")


def validate_alias(alias: object) -> str | None:
    # TODO(phase-3 M1): None -> None; must match ALIAS_PATTERN; reject RESERVED_ALIASES (case-insensitive).
    raise NotImplementedError("TODO(phase-3 M1): validate_alias")


def validate_expires_in_days(value: object) -> int | None:
    # TODO(phase-3 M1): None -> None; an int (but NOT a bool!) between 1 and 365.
    raise NotImplementedError("TODO(phase-3 M1): validate_expires_in_days")


def validate_days(value: object) -> int:
    # TODO(phase-3 M4): None -> 7; otherwise an integer (it arrives as a query string) between 1 and 90.
    raise NotImplementedError("TODO(phase-3 M4): validate_days")
