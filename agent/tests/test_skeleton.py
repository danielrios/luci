"""Skeleton integration tests for the Python agent.

Tests for US1 (health probes) and US2 (M2M auth). Written FIRST — these
MUST fail until the corresponding implementation tasks land. (TDD; §IV)
"""

from __future__ import annotations

import pytest
from httpx import ASGITransport, AsyncClient


@pytest.mark.asyncio
async def test_health_up(app_client: AsyncClient) -> None:
    """US1: With Postgres + Redis running, /health returns 200 with UP status."""
    response = await app_client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert "components" in body
    assert body["components"]["db"]["status"] == "UP"
    assert body["components"]["redis"]["status"] == "UP"


@pytest.mark.asyncio
async def test_health_db_down(monkeypatch: pytest.MonkeyPatch) -> None:
    """US1: Without a DB connection, /health returns 503 with db=DOWN."""
    # Force a bad DSN so the DB probe fails
    monkeypatch.setenv("LUCI_DB_DSN", "postgresql://bad:bad@localhost:1/nonexistent")
    monkeypatch.setenv("LUCI_REDIS_URL", "redis://localhost:6379/0")
    monkeypatch.setenv("LUCI_ENV", "local")
    monkeypatch.setenv("SPRING_BASE_URL", "http://localhost:8080")
    monkeypatch.setenv("LUCI_M2M_ISSUER", "test")
    monkeypatch.setenv("LUCI_M2M_AUDIENCE", "test")
    monkeypatch.setenv("LUCI_M2M_KID", "test-kid")
    monkeypatch.setenv("LUCI_M2M_PRIVATE_KEY_PEM", "test")
    monkeypatch.setenv("LUCI_M2M_PUBLIC_KEY_PEM", "test")
    monkeypatch.setenv("LUCI_M2M_JWKS_URL", "http://localhost:8080/.well-known/jwks.json")

    # Import after env is set so settings pick up test values
    from agent.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")
    assert response.status_code == 503
    body = response.json()
    assert body["components"]["db"]["status"] == "DOWN"


@pytest.mark.asyncio
async def test_health_redis_down(monkeypatch: pytest.MonkeyPatch) -> None:
    """US1: Without Redis, /health returns 503 with redis=DOWN."""
    monkeypatch.setenv("LUCI_DB_DSN", "postgresql://luci:luci@localhost:5432/luci")
    monkeypatch.setenv("LUCI_REDIS_URL", "redis://localhost:1/0")  # bad port
    monkeypatch.setenv("LUCI_ENV", "local")
    monkeypatch.setenv("SPRING_BASE_URL", "http://localhost:8080")
    monkeypatch.setenv("LUCI_M2M_ISSUER", "test")
    monkeypatch.setenv("LUCI_M2M_AUDIENCE", "test")
    monkeypatch.setenv("LUCI_M2M_KID", "test-kid")
    monkeypatch.setenv("LUCI_M2M_PRIVATE_KEY_PEM", "test")
    monkeypatch.setenv("LUCI_M2M_PUBLIC_KEY_PEM", "test")
    monkeypatch.setenv("LUCI_M2M_JWKS_URL", "http://localhost:8080/.well-known/jwks.json")

    from agent.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")
    assert response.status_code == 503
    body = response.json()
    assert body["components"]["redis"]["status"] == "DOWN"


@pytest.mark.asyncio
async def test_whoami_round_trip(app_client: AsyncClient) -> None:
    """US2: Mint M2M JWT → call POST /v1/internal/whoami on Spring → echo claims.

    This test requires both services running locally (make dev).
    It validates the full M2M auth round-trip. (T032, quickstart §4, SC-005)
    """
    import httpx

    from agent.http.m2m_auth import mint_m2m_token

    # Skip if Spring is not running
    try:
        async with httpx.AsyncClient() as probe:
            await probe.get("http://localhost:8080/health", timeout=2.0)
    except (httpx.ConnectError, httpx.ReadTimeout):
        pytest.skip("Spring Boot not running at localhost:8080")

    token = mint_m2m_token(
        user_id="test-user-123",
        intent="whoami",
        trace_id="aabbccdd00112233",
    )

    async with httpx.AsyncClient(  # luci-allow-spring-httpx: integration test round-trip
        base_url="http://localhost:8080",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    ) as client:
        response = await client.post("/v1/internal/whoami", content="{}")

    assert response.status_code == 200
    body = response.json()
    assert body["subject"] == "service:python-agent"
    assert body["user_id"] == "test-user-123"
    assert body["intent"] == "whoami"
    assert body["trace_id"] == "aabbccdd00112233"

    # Negative: no auth → 401
    async with httpx.AsyncClient(  # luci-allow-spring-httpx: integration test round-trip
        base_url="http://localhost:8080",
    ) as client:
        response = await client.post(
            "/v1/internal/whoami",
            content="{}",
            headers={"Content-Type": "application/json"},
        )
    assert response.status_code == 401


def test_settings_prod_fail_fast() -> None:
    """US3: Settings() raises ValueError in prod when required var is missing. (T045)"""
    import os

    os.environ["LUCI_ENV"] = "prod"
    os.environ["LUCI_DB_DSN"] = "postgresql://x:x@host/db?sslmode=require"
    os.environ["LUCI_REDIS_URL"] = "rediss://x:x@host:6379"
    os.environ["SPRING_BASE_URL"] = "http://localhost:8080"
    os.environ["LUCI_M2M_ISSUER"] = "test"
    os.environ["LUCI_M2M_AUDIENCE"] = "test"
    os.environ["LUCI_M2M_KID"] = "test-kid"
    os.environ["LUCI_M2M_PUBLIC_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_JWKS_URL"] = "http://localhost/.well-known/jwks.json"
    # Deliberately omit LUCI_M2M_PRIVATE_KEY_PEM
    os.environ.pop("LUCI_M2M_PRIVATE_KEY_PEM", None)

    from agent.config.settings import Settings

    with pytest.raises(Exception):
        Settings()  # type: ignore[call-arg]

    # Restore for other tests
    os.environ["LUCI_ENV"] = "local"

