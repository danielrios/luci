"""Pytest configuration for agent tests.

Provides AsyncClient fixture wrapping the FastAPI app.
"""

from __future__ import annotations

import os
from typing import AsyncGenerator

import pytest
from httpx import ASGITransport, AsyncClient

# Set test environment variables before importing the app
os.environ.setdefault("LUCI_ENV", "local")
os.environ.setdefault("LUCI_DB_DSN", "postgresql://luci:luci@localhost:5432/luci")
os.environ.setdefault("LUCI_REDIS_URL", "redis://localhost:6379/0")
os.environ.setdefault("SPRING_BASE_URL", "http://localhost:8080")
os.environ.setdefault("LUCI_M2M_ISSUER", "python-agent.luci.app")
os.environ.setdefault("LUCI_M2M_AUDIENCE", "finance-api.luci.app")
os.environ.setdefault("LUCI_M2M_KID", "test-kid")
os.environ.setdefault("LUCI_M2M_PRIVATE_KEY_PEM", "test")
os.environ.setdefault("LUCI_M2M_PUBLIC_KEY_PEM", "test")
os.environ.setdefault("LUCI_M2M_JWKS_URL", "http://localhost:8080/.well-known/jwks.json")
os.environ.setdefault("LUCI_LOG_LEVEL", "WARNING")


@pytest.fixture(autouse=True)
def reset_settings_cache() -> None:
    """Clear the settings lru_cache before each test so monkeypatched env vars take effect."""
    from agent.config.settings import get_settings

    get_settings.cache_clear()


@pytest.fixture
async def app_client() -> AsyncGenerator[AsyncClient, None]:
    """Async HTTP client wrapping the FastAPI test app."""
    from agent.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client
