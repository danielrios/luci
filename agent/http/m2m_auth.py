"""RS256 JWT minting for M2M auth.

Mints short-lived JWTs signed with the agent's private key for authenticating
calls to the Spring Boot Finance API. (FR-017, R-4)
"""

from __future__ import annotations

import time
import uuid

import jwt

from agent.config.settings import get_settings


def mint_m2m_token(
    user_id: str,
    intent: str,
    trace_id: str | None = None,
) -> str:
    """Mint an RS256-signed M2M JWT for the given user_id and intent."""
    now = int(time.time())
    if trace_id is None:
        trace_id = uuid.uuid4().hex

    # Decode the PEM from single-line env var format
    _s = get_settings()
    private_key_pem = _s.luci_m2m_private_key_pem.replace("\\n", "\n")

    payload = {
        "iss": _s.luci_m2m_issuer,
        "aud": _s.luci_m2m_audience,
        "sub": "service:python-agent",
        "iat": now,
        "exp": now + 15 * 60,  # 15 min TTL
        "user_id": user_id,
        "intent": intent,
        "trace_id": trace_id,
    }

    headers = {
        "kid": _s.luci_m2m_kid,
    }

    return jwt.encode(
        payload,
        private_key_pem,
        algorithm="RS256",
        headers=headers,
    )
