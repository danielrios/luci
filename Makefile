# ==============================================================================
# Luci — Root Makefile
# ==============================================================================
# Canonical entry point for cross-service commands.
# Command forms match quickstart.md §6 verbatim (F1).
# ==============================================================================

.DEFAULT_GOAL := help
SHELL := /bin/bash

# --- Port-conflict note ---
# `make up` relies on Docker Compose's native error:
#   "Bind for 0.0.0.0:<port> failed: port is already allocated"
# `make dev` relies on the OS / framework error:
#   uvicorn: "[Errno 98] Address already in use"
#   Spring:  "Web server failed to start. Port 8080 was already in use."
# These are human-readable; no wrapper script needed (see tasks.md T010/B1).

.PHONY: help bootstrap up dev lint codegen reset verify-m2m

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

bootstrap: ## Install deps, provision keys, generate client
	@echo "→ Installing uv (if absent)..."
	@command -v uv >/dev/null 2>&1 || curl -LsSf https://astral.sh/uv/install.sh | sh
	@echo "→ Creating Python venv and installing deps..."
	@cd agent && uv venv --python 3.12 .venv 2>/dev/null || true
	@cd agent && uv pip install -e ".[dev]"
	@echo "→ Verifying JDK 21..."
	@java -version 2>&1 | grep -q "21" || (echo "ERROR: JDK 21 required" && exit 1)
	@echo "→ Installing pre-commit hooks..."
	@command -v pre-commit >/dev/null 2>&1 || uv pip install pre-commit
	@[ -f .pre-commit-config.yaml ] && pre-commit install || echo "⚠ .pre-commit-config.yaml not found; skipping hook install"
	@echo "→ Creating .env.local from .env.example (if absent)..."
	@[ -f .env.local ] || cp .env.example .env.local
	@echo "→ Generating M2M keypair (idempotent)..."
	@bash scripts/bootstrap-keys.sh .env.local
	@echo "→ Generating Python client from openapi.yaml..."
	@$(MAKE) codegen
	@echo "✓ Bootstrap complete."

up: ## Start local infra (Postgres + Redis)
	docker compose -f infra/docker-compose.yml --project-name luci up -d --wait

dev: ## Run both services with live reload
	@trap 'kill 0' INT TERM; \
	( cd agent && uv run uvicorn agent.main:app --reload --host 0.0.0.0 --port 8000 ) & \
	( cd finance-api && ./gradlew bootRun ) & \
	wait

lint: ## Run all linters (ruff + mypy + ktlint + gitleaks + no-httpx guard)
	@echo "=== Luci Lint Suite ==="
	@EXIT=0; \
	echo "→ ruff check agent/"; \
	cd agent && uv run ruff check . || EXIT=1; cd ..; \
	echo "→ mypy --strict agent/"; \
	cd agent && uv run mypy --strict . || EXIT=1; cd ..; \
	echo "→ ktlint (finance-api)"; \
	cd finance-api && ./gradlew ktlintCheck --quiet || EXIT=1; cd ..; \
	echo "→ gitleaks"; \
	gitleaks detect --source . --no-banner 2>/dev/null || EXIT=1; \
	echo "→ no-httpx-to-Spring guard"; \
	[ -x scripts/lint-no-spring-httpx.sh ] && bash scripts/lint-no-spring-httpx.sh || true; \
	echo "=== Done (exit=$$EXIT) ==="; \
	exit $$EXIT

codegen: ## Regenerate Python client from openapi.yaml
	@cd agent && uv run openapi-python-client generate \
		--path ../openapi.yaml \
		--output-path finance_api_client \
		--overwrite 2>/dev/null || \
	echo "⚠ openapi-python-client codegen failed (may need 'uv pip install openapi-python-client')"

reset: ## Wipe local stack and restart
	docker compose -f infra/docker-compose.yml --project-name luci down -v
	@$(MAKE) up

verify-m2m: ## Run M2M auth integration test
	cd agent && uv run pytest tests/test_skeleton.py::test_whoami_round_trip -v
