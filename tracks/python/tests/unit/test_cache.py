"""RedisLinkCache circuit breaker (v1.1, from the Redis-down game day). No Redis needed: fake client + clock."""

import redis

from app.cache import RedisLinkCache
from app.models import CachedLink


class FakeClock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


class FlakyRedis:
    """Counts calls; raises ConnectionError while `down` is True."""

    def __init__(self):
        self.down = False
        self.calls = 0
        self.data: dict[str, str] = {}

    def _call(self):
        self.calls += 1
        if self.down:
            raise redis.ConnectionError("Temporary failure in name resolution")

    def get(self, key):
        self._call()
        return self.data.get(key)

    def set(self, key, value, ex=None):
        self._call()
        self.data[key] = value

    def delete(self, key):
        self._call()
        self.data.pop(key, None)

    def ping(self):
        self._call()
        return True


LINK = CachedLink(id=1, long_url="https://example.com", expires_at=None)


def make():
    client, clock = FlakyRedis(), FakeClock()
    return RedisLinkCache("redis://unused", cooldown_seconds=5, clock=clock, client=client), client, clock


def test_round_trip_when_redis_is_up():
    cache, _, _ = make()
    cache.put("abc", LINK, 60)
    assert cache.get("abc") == LINK
    assert cache.get("missing") is None


def test_failure_opens_the_circuit_and_skips_redis_during_cooldown():
    cache, client, clock = make()
    client.down = True
    assert cache.get("abc") is None
    assert client.calls == 1
    for _ in range(10):
        assert cache.get("abc") is None
        cache.put("abc", LINK, 60)
    assert client.calls == 1  # no further slow calls while the circuit is open
    clock.now += 5
    assert cache.get("abc") is None
    assert client.calls == 2  # one probe after the cool-down


def test_circuit_closes_again_after_cooldown_when_redis_is_back():
    cache, client, clock = make()
    client.down = True
    cache.put("abc", LINK, 60)
    client.down = False
    assert cache.get("abc") is None  # still open: skipped
    clock.now += 5
    cache.put("abc", LINK, 60)
    assert cache.get("abc") == LINK


def test_successful_health_ping_closes_the_circuit_immediately():
    cache, client, _ = make()
    client.down = True
    assert cache.get("abc") is None
    assert cache.status() == "DOWN"
    client.down = False
    assert cache.status() == "UP"
    cache.put("abc", LINK, 60)
    assert cache.get("abc") == LINK


def test_delete_is_always_attempted_and_failures_are_swallowed():
    cache, client, _ = make()
    client.down = True
    cache.delete("abc")
    cache.delete("abc")
    assert client.calls == 2
