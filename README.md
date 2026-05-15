# Luci

Luci is a Brazilian personal-finance agent delivered through Telegram. It combines an LLM-powered orchestrator for intent parsing with a strict, deterministic Spring Boot backend for all financial calculations.

## Architecture

The stack is composed of two main services to enforce the **"Zero LLM arithmetic"** policy:

- **Python Agent (`agent/`)**: Built with Pydantic AI and Gemini 2.5 Flash Lite (via Vertex AI ZDR, São Paulo). Handles I/O, intent parsing, multimodal extraction (audio, PDF, photo), and chart rendering (Matplotlib).
- **Spring Boot Finance API (`finance-api/`)**: Built with JDK 21. Owns all deterministic money math, Mercado Pago Pix Automático billing, the subscription state machine, the audit log, and the LGPD deletion worker.
- **Data Layer**: Supabase Postgres 15 + pgvector + pgmq as the single source of truth. Redis (Upstash) for rate-limit counters.

## Authoritative Documentation

Please read these documents before contributing:
1. **Constitution**: `.specify/memory/constitution.md` (Binding invariants and principles)
2. **Technical Design**: `docs/Luci_Tech_Design_v3.md` (Architecture, schema, contracts)
3. **Product Requirements**: `docs/PRD_Luci_v2.md` (Intents, SLOs, monetization)
4. **Agent Guidelines**: `AGENTS.md` (AI Agent workflows and boundaries)

## Contract First

`openapi.yaml` at the repo root is the single seam between the Agent and the Finance API. 
The Spring API serves this spec, and the Python `httpx` client is strictly auto-generated from it.

## Local Development

Development environments are managed via the `Makefile`:

```bash
make bootstrap   # uv + JDK 21 + pre-commit; .env.example → .env.local
make up          # docker-compose up -d + Flyway migrate + seed
make dev         # tmux: agent (uvicorn --reload), spring (gradle bootRun), log tail
make seed        # Seeds 3 users, 200 txns, 1 ACTIVE subscription
make reset       # Full local wipe + reseed
```

External services are mocked locally:
- Gemini is mocked via `respx` cassettes in `agent/tests/fixtures/llm/`
- Mercado Pago is mocked via MockServer in `infra/mocks/mercadopago/`

## Spec Kit Workflow

This project uses Spec Kit for Spec-Driven Development. Features flow through:
`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement` → `/speckit-analyze`
