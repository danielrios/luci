"""Health check routes for the Python agent.

GET /health probes Postgres (SELECT 1) and Redis (PING) in parallel using
asyncio.gather with per-probe timeout of 1s. Returns Actuator-shaped JSON.
HTTP 200 on UP, 503 on DOWN. (FR-023, FR-024, FR-025, FR-026, R-7, C1)
"""

from __future__ import annotations

import asyncio
from typing import TypedDict

import psycopg
import redis.asyncio as aioredis
import structlog
from fastapi import APIRouter, Response
from fastapi.responses import JSONResponse

from agent.config.settings import get_settings


class ProbeResult(TypedDict):
    status: str
    details: dict[str, str]


logger = structlog.get_logger(__name__)

router = APIRouter()

PROBE_TIMEOUT_SECONDS = 1.0


async def _probe_db() -> ProbeResult:
    """Probe Postgres with SELECT 1."""
    try:
        async with asyncio.timeout(PROBE_TIMEOUT_SECONDS):
            conn = await psycopg.AsyncConnection.connect(
                get_settings().luci_db_dsn,
                autocommit=True,
            )
            async with conn:
                async with conn.cursor() as cur:
                    await cur.execute("SELECT 1")
            return {"status": "UP", "details": {"database": "PostgreSQL"}}
    except Exception as exc:
        logger.warning("db_probe_failed", error=str(exc))
        return {"status": "DOWN", "details": {"error": str(exc)}}


async def _probe_redis() -> ProbeResult:
    """Probe Redis with PING."""
    try:
        async with asyncio.timeout(PROBE_TIMEOUT_SECONDS):
            client = aioredis.from_url(get_settings().luci_redis_url)
            try:
                pong = await client.ping()  # type: ignore[misc]
                if not pong:
                    return {"status": "DOWN", "details": {"error": "PING returned False"}}
                info = await client.info("server")
                version = info.get("redis_version", "unknown")
                return {"status": "UP", "details": {"version": version}}
            finally:
                await client.aclose()
    except Exception as exc:
        logger.warning("redis_probe_failed", error=str(exc))
        return {"status": "DOWN", "details": {"error": str(exc)}}


@router.get("/health")
async def health() -> Response:
    """Health check endpoint — probes DB and Redis in parallel."""
    db_result, redis_result = await asyncio.gather(
        _probe_db(),
        _probe_redis(),
    )

    aggregate = "UP"
    if db_result["status"] == "DOWN" or redis_result["status"] == "DOWN":
        aggregate = "DOWN"

    body = {
        "status": aggregate,
        "components": {
            "db": db_result,
            "redis": redis_result,
        },
    }

    status_code = 200 if aggregate == "UP" else 503
    return JSONResponse(content=body, status_code=status_code)
