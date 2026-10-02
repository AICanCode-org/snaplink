"""Short-code generation: random Base62 from a cryptographically secure source."""

from __future__ import annotations

import string

ALPHABET = string.ascii_letters + string.digits  # 62 characters
CODE_LENGTH = 7


def generate_code(length: int = CODE_LENGTH) -> str:
    # TODO(phase-3 M1): return `length` characters picked from ALPHABET.
    # Use the `secrets` module (cryptographically secure), NOT `random`. Why? See ADR-0001.
    raise NotImplementedError("TODO(phase-3 M1): generate_code")
