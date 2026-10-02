"""Short-code generation: random Base62 from a cryptographically secure source."""

from __future__ import annotations

import secrets
import string

ALPHABET = string.ascii_letters + string.digits  # 62 characters
CODE_LENGTH = 7


def generate_code(length: int = CODE_LENGTH) -> str:
    return "".join(secrets.choice(ALPHABET) for _ in range(length))
