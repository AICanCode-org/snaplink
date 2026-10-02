from datetime import timedelta

import pytest

from app.cache import InMemoryCache
from app.errors import ApiError
from app.repository import InMemoryLinkRepository
from app.service import MAX_CACHE_TTL_SECONDS, LinkService


@pytest.fixture
def repo():
    return InMemoryLinkRepository()


@pytest.fixture
def cache():
    return InMemoryCache()


@pytest.fixture
def service(repo, cache, clock):
    return LinkService(repo, cache, clock)


def test_create_random_link(service, clock):
    link = service.create("https://www.aicancode.org")
    assert len(link.code) == 7
    assert link.custom is False
    assert link.created_at == clock.now
    assert link.expires_at is None


def test_create_with_alias_and_expiry(service, clock):
    link = service.create("https://www.aicancode.org", alias="aicancode", expires_in_days=30)
    assert link.code == "aicancode" and link.custom is True
    assert link.expires_at == clock.now + timedelta(days=30)


def test_duplicate_alias_is_409(service):
    service.create("https://a.com", alias="taken")
    with pytest.raises(ApiError) as err:
        service.create("https://b.com", alias="taken")
    assert err.value.status == 409


def test_random_code_collision_is_retried(repo, cache, clock):
    codes = iter(["SAME123", "SAME123", "OTHER45"])
    service = LinkService(repo, cache, clock, code_generator=lambda: next(codes))
    assert service.create("https://a.com").code == "SAME123"
    assert service.create("https://b.com").code == "OTHER45"


def test_gives_up_after_too_many_collisions(repo, cache, clock):
    service = LinkService(repo, cache, clock, code_generator=lambda: "SAME123")
    service.create("https://a.com")
    with pytest.raises(RuntimeError):
        service.create("https://b.com")


def test_resolve_records_click_and_populates_cache(service, cache, repo):
    link = service.create("https://www.aicancode.org")
    assert service.resolve(link.code) == "https://www.aicancode.org"
    assert link.code in cache.store
    assert repo.find_by_code(link.code).click_count == 1


def test_resolve_uses_cache_on_second_hit(service, cache, repo):
    link = service.create("https://www.aicancode.org")
    service.resolve(link.code)
    repo.find_by_code = lambda code: pytest.fail("should have been served from cache")
    assert service.resolve(link.code) == "https://www.aicancode.org"


def test_unknown_code_is_404(service):
    with pytest.raises(ApiError) as err:
        service.resolve("nope123")
    assert err.value.status == 404


def test_expired_link_is_410_even_when_cached(service, clock, cache):
    link = service.create("https://a.com", expires_in_days=1)
    service.resolve(link.code)  # now cached
    clock.advance(days=1)  # exactly at expiry -> expired
    with pytest.raises(ApiError) as err:
        service.resolve(link.code)
    assert err.value.status == 410
    assert link.code not in cache.store
    assert service.get(link.code).click_count == 1  # the expired attempt was not counted


def test_cache_ttl_never_outlives_expiry(service, clock):
    from app.models import CachedLink

    soon = CachedLink(1, "https://a.com", clock.now + timedelta(seconds=90))
    never = CachedLink(2, "https://b.com", None)
    assert LinkService._cache_ttl(soon, clock.now) == 90
    assert LinkService._cache_ttl(never, clock.now) == MAX_CACHE_TTL_SECONDS


def test_delete_removes_link_and_cache(service, cache):
    link = service.create("https://a.com")
    service.resolve(link.code)
    service.delete(link.code)
    assert link.code not in cache.store
    with pytest.raises(ApiError):
        service.get(link.code)


def test_delete_unknown_is_404(service):
    with pytest.raises(ApiError) as err:
        service.delete("missing")
    assert err.value.status == 404


def test_click_failure_does_not_break_redirect(service, repo):
    link = service.create("https://a.com")

    def boom(*_args):
        raise RuntimeError("db hiccup")

    repo.record_click = boom
    assert service.resolve(link.code) == "https://a.com"


def test_stats_are_zero_filled_oldest_first(service, clock):
    link = service.create("https://a.com")
    service.resolve(link.code)
    clock.advance(days=2)
    service.resolve(link.code)
    service.resolve(link.code)
    found, daily = service.stats(link.code, "7")
    assert found.click_count == 3
    assert len(daily) == 7
    assert daily[-1].day == clock.now.date() and daily[-1].clicks == 2
    assert daily[-3].clicks == 1
    assert sum(d.clicks for d in daily) == 3
