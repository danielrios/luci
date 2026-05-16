"""Tests for prod-mode settings validation.

Validates that in prod mode:
- LUCI_DB_DSN without sslmode=require is rejected
- LUCI_REDIS_URL without rediss:// is rejected
- Local mode accepts both forms
(E1, FR-010, FR-012, T042)
"""

from __future__ import annotations

import os

import pytest


def test_prod_rejects_plain_db_dsn() -> None:
    """In prod, LUCI_DB_DSN without sslmode=require raises ValueError."""
    os.environ["LUCI_ENV"] = "prod"
    os.environ["LUCI_DB_DSN"] = "postgresql://user:pass@host/db"
    os.environ["LUCI_REDIS_URL"] = "rediss://default:pw@host:6379"
    os.environ["SPRING_BASE_URL"] = "http://localhost:8080"
    os.environ["LUCI_M2M_ISSUER"] = "test"
    os.environ["LUCI_M2M_AUDIENCE"] = "test"
    os.environ["LUCI_M2M_KID"] = "test-kid"
    os.environ["LUCI_M2M_PRIVATE_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_PUBLIC_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_JWKS_URL"] = "http://localhost/.well-known/jwks.json"

    # Re-import to force fresh Settings construction
    from agent.config.settings import Settings

    with pytest.raises(ValueError, match="LUCI_DB_DSN"):
        Settings()  # type: ignore[call-arg]


def test_prod_rejects_plain_redis_url() -> None:
    """In prod, LUCI_REDIS_URL without rediss:// raises ValueError."""
    os.environ["LUCI_ENV"] = "prod"
    os.environ["LUCI_DB_DSN"] = "postgresql://user:pass@host/db?sslmode=require"
    os.environ["LUCI_REDIS_URL"] = "redis://default:pw@host:6379"
    os.environ["SPRING_BASE_URL"] = "http://localhost:8080"
    os.environ["LUCI_M2M_ISSUER"] = "test"
    os.environ["LUCI_M2M_AUDIENCE"] = "test"
    os.environ["LUCI_M2M_KID"] = "test-kid"
    os.environ["LUCI_M2M_PRIVATE_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_PUBLIC_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_JWKS_URL"] = "http://localhost/.well-known/jwks.json"

    from agent.config.settings import Settings

    with pytest.raises(ValueError, match="LUCI_REDIS_URL"):
        Settings()  # type: ignore[call-arg]


def test_prod_accepts_tls_urls() -> None:
    """In prod, TLS URLs are accepted."""
    os.environ["LUCI_ENV"] = "prod"
    os.environ["LUCI_DB_DSN"] = "postgresql://user:pass@host/db?sslmode=require"
    os.environ["LUCI_REDIS_URL"] = "rediss://default:pw@host:6379"
    os.environ["SPRING_BASE_URL"] = "http://localhost:8080"
    os.environ["LUCI_M2M_ISSUER"] = "test"
    os.environ["LUCI_M2M_AUDIENCE"] = "test"
    os.environ["LUCI_M2M_KID"] = "test-kid"
    os.environ["LUCI_M2M_PRIVATE_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_PUBLIC_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_JWKS_URL"] = "http://localhost/.well-known/jwks.json"

    from agent.config.settings import Settings

    s = Settings()  # type: ignore[call-arg]
    assert s.luci_env == "prod"


def test_local_accepts_both_forms() -> None:
    """In local mode, plain URLs are accepted."""
    os.environ["LUCI_ENV"] = "local"
    os.environ["LUCI_DB_DSN"] = "postgresql://luci:luci@localhost/luci"
    os.environ["LUCI_REDIS_URL"] = "redis://localhost:6379/0"
    os.environ["SPRING_BASE_URL"] = "http://localhost:8080"
    os.environ["LUCI_M2M_ISSUER"] = "test"
    os.environ["LUCI_M2M_AUDIENCE"] = "test"
    os.environ["LUCI_M2M_KID"] = "test-kid"
    os.environ["LUCI_M2M_PRIVATE_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_PUBLIC_KEY_PEM"] = "test"
    os.environ["LUCI_M2M_JWKS_URL"] = "http://localhost/.well-known/jwks.json"

    from agent.config.settings import Settings

    s = Settings()  # type: ignore[call-arg]
    assert s.luci_env == "local"
