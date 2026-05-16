"""Prometheus metrics middleware for the Python agent.

Exposes a Counter for HTTP requests and mounts the prometheus_client ASGI app
at GET /metrics. (FR-026b, R-8)
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable

from prometheus_client import Counter, make_asgi_app
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

http_requests_total = Counter(
    "http_requests_total",
    "Total HTTP requests",
    ["method", "route", "status"],
)


class PrometheusMiddleware(BaseHTTPMiddleware):
    """Middleware to count HTTP requests by method, route, and status."""

    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        response: Response = await call_next(request)
        route = request.scope.get("path", request.url.path)
        http_requests_total.labels(
            method=request.method,
            route=route,
            status=str(response.status_code),
        ).inc()
        return response


def create_metrics_app() -> ASGIApp:
    """Create the prometheus_client ASGI app for mounting at /metrics."""
    return make_asgi_app()
