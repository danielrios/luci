"""Application settings loaded from environment variables via pydantic-settings.

Fail-fast at import if any required variable is absent. (FR-010)
In prod mode, enforces TLS on DB (sslmode=require) and Redis (rediss://).
(FR-012, data-model.md §1, Story 3 AS-4, T046)
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Luci agent configuration from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env.local",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # --- Profile ---
    luci_env: str = "local"

    # --- Database (libpq/psycopg) ---
    luci_db_dsn: str

    # --- Cache ---
    luci_redis_url: str

    # --- Inter-service ---
    spring_base_url: str

    # --- M2M JWT ---
    luci_m2m_issuer: str
    luci_m2m_audience: str
    luci_m2m_kid: str
    luci_m2m_private_key_pem: str
    luci_m2m_public_key_pem: str
    luci_m2m_jwks_url: str

    # --- Observability ---
    luci_log_level: str = "INFO"

    @field_validator("luci_env")
    @classmethod
    def validate_env(cls, v: str) -> str:
        if v not in ("local", "prod"):
            msg = f"LUCI_ENV must be 'local' or 'prod', got '{v}'"
            raise ValueError(msg)
        return v

    @model_validator(mode="after")
    def validate_prod_tls(self) -> "Settings":
        """In prod, enforce TLS on DB and Redis connections."""
        if self.luci_env != "prod":
            return self

        if "sslmode=require" not in self.luci_db_dsn:
            msg = (
                "LUCI_DB_DSN must include 'sslmode=require' in prod. "
                f"Got: {self.luci_db_dsn}"
            )
            raise ValueError(msg)

        if not self.luci_redis_url.startswith("rediss://"):
            msg = (
                "LUCI_REDIS_URL must start with 'rediss://' (TLS) in prod. "
                f"Got: {self.luci_redis_url}"
            )
            raise ValueError(msg)

        return self


@lru_cache(maxsize=1)
def get_settings() -> "Settings":
    """Return the cached Settings singleton. Call get_settings.cache_clear() in tests."""
    return Settings()  # type: ignore[call-arg]
