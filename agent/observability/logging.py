"""Structured JSON logging configuration for the Python agent.

Uses structlog with JSONRenderer. All log events are structured and field-tagged
(not free-text) so a downstream PiiRedactor filter can reliably identify and
redact PII-bearing fields. (FR-026a, Constitution §II)
"""

from __future__ import annotations

import logging
import os
import sys
from typing import cast

import structlog


def configure_logging() -> None:
    """Configure structlog for JSON output with stdlib forwarding."""
    log_level = os.environ.get("LUCI_LOG_LEVEL", "INFO").upper()

    # Configure stdlib logging to forward through structlog
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=getattr(logging, log_level, logging.INFO),
    )

    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.filter_by_level,
            structlog.stdlib.add_logger_name,
            structlog.stdlib.add_log_level,
            structlog.stdlib.PositionalArgumentsFormatter(),
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.processors.UnicodeDecoder(),
            structlog.processors.JSONRenderer(),
        ],
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )


def get_logger(name: str) -> structlog.stdlib.BoundLogger:
    """Get a structlog logger bound with the service name."""
    return cast(structlog.stdlib.BoundLogger, structlog.get_logger(name).bind(service="agent"))
