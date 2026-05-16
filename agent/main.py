"""FastAPI application factory for the Python agent.

Configures structlog, mounts /health router, /metrics ASGI sub-app,
and the orchestrator router stub. (FR-003)
"""

from __future__ import annotations

from fastapi import FastAPI

from agent.config.settings import get_settings
from agent.health.routes import router as health_router
from agent.observability.logging import configure_logging
from agent.observability.metrics import PrometheusMiddleware, create_metrics_app

configure_logging()
get_settings()  # fail-fast: validate all required env vars at startup

app = FastAPI(
    title="Luci Agent",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)

# Middleware
app.add_middleware(PrometheusMiddleware)

# Routes
app.include_router(health_router)

# Prometheus metrics sub-app
metrics_app = create_metrics_app()
app.mount("/metrics", metrics_app)
