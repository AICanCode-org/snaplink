# ADR-0001: Random Base62 short codes with retry on collision

* **Status:** accepted
* **Date:** 2026-10-02

## Context
Codes must be short, URL-safe, hard to enumerate and unique. We expect ≤ 1 M links (requirements §7).

## Options considered
1. **Auto-increment id → Base62** — short and collision-free, but sequential: anyone can enumerate
   every link by counting (privacy leak), and it couples codes to one database sequence.
2. **Hash of the URL (e.g. first 7 chars of SHA-256)** — deterministic, but the same URL always gets the
   same code (no per-user links/expiry), and truncated hashes still collide, so we need retry anyway.
3. **Random 7-char Base62 from a CSPRNG + unique constraint + retry** — unguessable, stateless.
   62⁷ ≈ 3.5 × 10¹²; at 1 M links a new code collides with probability ≈ 3 × 10⁻⁷.

## Decision
Option 3. Generate with `SecureRandom` / `secrets` / `crypto.randomInt`, insert, and on a unique-violation
retry up to 5 times (probability that all 5 collide is ~10⁻³³). Custom aliases are never retried — they
return `409`.

## Consequences
* No "check then insert" race: the database constraint decides.
* Codes reveal nothing about volume or order.
* Revisit if we approach ~10⁹ links (then 8 chars).
