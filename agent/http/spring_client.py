"""Thin wrapper around the generated Finance API client.

Returns an authenticated httpx client configured with SPRING_BASE_URL
and the M2M bearer token. (FR-002b)
"""

from __future__ import annotations

from agent.config.settings import get_settings
from agent.finance_api_client import AuthenticatedClient
from agent.http.m2m_auth import mint_m2m_token


def get_spring_client(
    user_id: str,
    intent: str,
    trace_id: str | None = None,
) -> AuthenticatedClient:
    """Get an authenticated Finance API client with M2M bearer token."""
    token = mint_m2m_token(user_id=user_id, intent=intent, trace_id=trace_id)
    return AuthenticatedClient(
        base_url=get_settings().spring_base_url,
        token=token,
    )
