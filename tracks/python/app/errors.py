"""Domain errors that map 1:1 to RFC 9457 problem responses."""

from __future__ import annotations

PROBLEM_BASE = "https://snaplink.dev/problems/"


class ApiError(Exception):
    def __init__(self, status: int, slug: str, title: str, detail: str | None = None):
        super().__init__(detail or title)
        self.status = status
        self.slug = slug
        self.title = title
        self.detail = detail

    def to_problem(self) -> dict:
        body = {"type": PROBLEM_BASE + self.slug, "title": self.title, "status": self.status}
        if self.detail:
            body["detail"] = self.detail
        return body


def validation_error(detail: str) -> ApiError:
    return ApiError(400, "validation-error", "Validation failed", detail)


def not_found(code: str) -> ApiError:
    return ApiError(404, "not-found", "Link not found", f"No link with code '{code}'")


def expired(code: str) -> ApiError:
    return ApiError(410, "expired", "Link expired", f"Link '{code}' has expired")


def alias_taken(alias: str) -> ApiError:
    return ApiError(409, "alias-taken", "Alias already taken", f"The alias '{alias}' is already in use")


class DuplicateCodeError(Exception):
    """Raised by a repository when the unique constraint on links.code is violated."""
