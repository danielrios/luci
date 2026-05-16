# AGENTS.md

This file provides guidance to AI Agents when working with code in this repository.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

## Repository status

This repo currently holds **specs and governance only** — no application code has landed yet. The runtime stack described below is the *approved target*, not what is on disk. Day-1 onboarding (Makefile, `docker-compose.yml`, `agent/`, `finance-api/`, `openapi.yaml`) will appear under feature branches as Spec Kit feeds them in.

Directories present today:

- `docs/` — `PRD_Luci_v2.md` (product), `Luci_Tech_Design_v3.md` (TDD 3.1, approved).
- `.specify/` — Spec Kit workflow: `memory/constitution.md`, `templates/`, `extensions.yml` (hooks), `workflows/`, `integrations/`.
- `.claude/skills/` — mirrored Spec Kit skill packs (`speckit-*`).

## Authoritative documents — read these before changing anything

1. `.specify/memory/constitution.md` (v1.0.0) — **binding**. Seven principles; conflicts resolve in favour of the constitution until amended.
2. `docs/Luci_Tech_Design_v3.md` — technical source of truth (architecture, schema, contracts, CI gates).
3. `docs/PRD_Luci_v2.md` — product source of truth (intents, SLOs, monetization).

If the TDD and the constitution disagree, the constitution wins and the TDD must be updated within one sprint.

## What Luci is (one paragraph)

Luci is a Brazilian personal-finance agent delivered through Telegram. A **Python Agent** (Pydantic AI + Gemini 2.5 Flash Lite via Vertex AI ZDR, São Paulo) handles I/O, intent parsing, multimodal extraction (audio, PDF, photo), and Matplotlib chart rendering. A **Spring Boot Finance API** (JDK 21) owns *all* deterministic money math, Mercado Pago Pix Automático billing, the subscription state machine, the audit log, and the LGPD deletion worker. **Supabase Postgres 15 + pgvector + pgmq** is the single source of truth; **Redis (Upstash)** holds rate-limit counters with Spring as authoritative. Inter-service auth is M2M RS256 JWT (15-min TTL) + HMAC on sensitive endpoints; mTLS is deferred to Phase 4.

## Hard invariants you cannot violate

These are constitutional NON-NEGOTIABLES — block your own work if a change would breach them:

1. **Zero LLM arithmetic.** Money math is a deterministic Spring tool call. LLM output schemas must not contain `balance`, `total`, `projection`, or any computed numeric field. `DECIMAL(14,2)` for transactions, `DECIMAL(18,4)` for investments. `float`/`Float`/`Double`/`Number` for currency is a CI failure. The `zero_math.jsonl` eval must stay at 100% tool-routed.
2. **LGPD lifecycle.** `/cancel` is one tap; soft-delete is immediate; hard-purge runs within 15 days (DB-level CHECK enforces it). Audit log entries get pseudonymized (`sha256(user_id || pepper)`) at purge and retained 5 years. PDFs purge from object storage within 24h of ingestion.
3. **Contract first.** `openapi.yaml` at repo root is the single seam. Spring's served spec (springdoc) must match the committed file; Python's `httpx` client must be regenerated from it (`openapi-python-client`). Hand-written `httpx` calls to Spring are blocked by lint. PRs touching both `agent/` and `finance-api/` without `openapi.yaml` are rejected by CI.
4. **TDD + eval gates.** Tests precede implementation (Testcontainers for Spring; `respx` + recorded Gemini cassettes for Python). PR gate runs the eval suite when prompts/tools/schemas/validators/fewshots change: `text_intents ≥ 95% F1`, `adversarial ≥ 80%`, `prompt_injection = 0 exploits`, `zero_math = 100%`. Skip flags are forbidden.
5. **Idempotency, audit, observability from day 1.** Every state-mutating POST requires `Idempotency-Key`; the key row commits in the same transaction as the change. Every `@agent.tool` audit-logs via `AuditLogAspect`. Three pillars (structured JSON logs, Prometheus, OTel traces) plus Langfuse wrap every `Agent.run()`. Per-user LLM cost kill-switch at R$ 5.00/day.
6. **Prompts/schemas/migrations are code.** System prompts live in `agent/orchestrator/prompts/system_v{N}.md` with a `current.md` symlink. Shipped prompts are marked `# FROZEN: shipped <YYYY-MM-DD>` and become append-only — CI blocks edits. User input must be wrapped in delimiter templates (e.g. `<user_supplied_document>...</user_supplied_document>`); never concatenated into the prompt. Tool surface is static at boot. Migrations are Flyway-only, additive on a single deploy (`DROP COLUMN` and type-narrowing require a 2-release deprecation window).
7. **T1/T2/T3 tier discipline.** T1 (Governance) owns `openapi.yaml`, Flyway migrations, `agent/orchestrator/prompts/`, `agent/orchestrator/schemas.py`, ADRs. T2 (Execution) writes controllers/services/tools/tests that *conform* to T1. T3 (wide-context refactor) is read-only and lands changes via PR. Tool swaps within a tier are silent; cross-tier reassignment needs an ADR.

## Working with Spec Kit (the workflow on this repo)

Features flow through five phases. Each step lists the recommended model — use your judgement, but default to the table.

### Phase 1 — Discovery & Scoping ("what")

| Step | Command | Purpose | Model |
|---|---|---|---|
| 1 | `/speckit-specify` | PRD → `spec.md` | Sonnet |
| 2 | `/speckit-clarify` | Resolve ambiguities | Sonnet |
| 3 | `/speckit-checklist` | Validate spec completeness | Sonnet |

### Phase 2 — Architecture & Tasking ("how")

| Step | Command | Purpose | Model |
|---|---|---|---|
| 4 | `/speckit-plan` | Architecture, contracts, routes | **Opus** |
| 5 | `/speckit-tasks` | Slice into ordered tasks | Sonnet |
| 6 | `/speckit-superb-review` | Audit task granularity + spec coverage | **Opus** |

### Phase 3 — Execution & TDD ("build")

| Step | Command | Purpose | Model |
|---|---|---|---|
| 7 | `/speckit-superb-tdd` | Enforce failing test first (RED) | Sonnet |
| 8 | `/speckit-implement` | Write code + tests (GREEN → REFACTOR) | Opus 4.6 (thinking) / Sonnet (thinking) |
| 9 | `/speckit-superb-debug` | Root-cause investigation (optional) | **Opus** |

### Phase 4 — Quality Gates ("prove it")

| Step | Command | Purpose | Model |
|---|---|---|---|
| 10 | `/speckit-superb-verify` | Evidence of passing tests | Sonnet |
| 11 | `/speckit-superb-critique` | Architectural drift analysis | **Opus** |
| 12 | `/speckit-superb-respond` | Address review findings | Sonnet |

### Phase 5 — Integration ("ship it")

| Step | Command | Purpose | Model |
|---|---|---|---|
| 13 | `/speckit-superb-finish` | Consolidate, PR, merge decision | Sonnet |

Supporting commands (use anytime): `/speckit-constitution`, `/speckit-checklist`, `/speckit-taskstoissues`, `/speckit-analyze`.

**Mandatory hooks** (declared in `.specify/extensions.yml`, do not disable):

- `speckit.git.initialize` — runs before `/speckit-constitution`.
- `speckit.git.feature` — runs before `/speckit-specify` (creates the feature branch).
- `speckit.superb.tdd` — runs before `/speckit-implement`. Bridges the superpowers TDD skill so a failing test exists *before* any code is written.
- `speckit.superb.verify` — runs after `/speckit-implement`. Blocks false completions; runs spec-coverage checks.

All other `speckit.git.commit` hooks are **optional auto-commit** prompts — accept them when you've reached a coherent checkpoint, decline when you haven't.

## Tier mapping (so you know which surface you're touching)

| Surface | Tier | Modification rule |
|---|---|---|
| `openapi.yaml`, `shared/`, `docs/adr/` | T1 | Governance task only |
| `agent/orchestrator/prompts/`, `schemas.py`, `output_validators.py` | T1 | Governance task; frozen prompts append-only |
| `finance-api/src/main/resources/db/migration/` (Flyway) | T1 | Governance task; additive only |
| Controllers, services, agent tools, tests | T2 | Conforms to T1 — never redefines |
| Wide-context refactors | T3 | Read-only analysis; patches via PR |

## Anti-vibe rules (CI will enforce these)

- **Python:** `mypy --strict`; no `Any` in function signatures; no `# type: ignore` without a justification comment; `Decimal` mandatory for currency; no `print()` — only `logger.*`.
- **JVM (Kotlin/Java):** no `Double`/`Float` for currency (Spotbugs); no raw stack trace returns from controllers (PMD); no `@SuppressWarnings("all")`.
- **Both:** no SQL string concatenation; every `@PostMapping` is `@Idempotent`; every outbound `httpx` POST injects `Idempotency-Key`; no secrets in repo/logs/env-debug endpoints (gitleaks pre-commit + CI).
- **Contract drift signals (auto-reject):** PR touches both services without `openapi.yaml`; PR touches `openapi.yaml` without a generated-client touch; edit to a `# FROZEN` prompt; hand-written `httpx` to Spring.

## Simplicity & evolution rules

- **Prefer boring architecture over extensible architecture.** A flat function that works beats a plugin system that might.
- **No abstraction without present-day pressure.** If only one class implements an interface, collapse it. Extract when the second consumer appears, not before.
- **Prefer schema extensibility over enum proliferation.** Domain concepts that will grow (categories, intents, agent types) should be data-driven rows, not hardcoded enums or `switch` arms. If adding a new concept requires a code deploy, the design is wrong.
- **New domain concepts should not require orchestration rewrites.** Adding a tool, an intent, or a transaction type should be additive (new file, new row, new config entry) — not a refactor of existing control flow.
- **Optimize for deletion and refactoring, not permanence.** Small, decoupled units that can be ripped out in one PR are better than deep hierarchies that resist change.
- **Reject cosmetic abstraction.** `Controller → Service → DomainService → RepositoryAdapter → ProviderFactory` for a single flow is a smell, not sophistication. Every layer must justify its existence with a concrete, present-day reason.

## Commands

Build/lint/test commands will be added here once `agent/`, `finance-api/`, and the `Makefile` land. Per TDD §18, the target Day-1 surface is:

```
make bootstrap   # uv + JDK 21 + pre-commit; .env.example → .env.local
make up          # docker-compose up -d + Flyway migrate + seed
make dev         # tmux: agent (uvicorn --reload), spring (gradle bootRun), log tail
make seed        # 3 users / 200 txns / 1 ACTIVE subscription
make reset       # full local wipe + reseed
```

External services in local dev are mocked by default: Gemini via `respx` cassettes in `agent/tests/fixtures/llm/`, Mercado Pago via MockServer expectations in `infra/mocks/mercadopago/`, Supabase Storage via MinIO. To hit real Vertex AI (only when recording new cassettes), set `LUCI_LLM_MODE=live`.

## Out of scope for MVP (will be rejected unless an amendment lands)

Open Finance aggregation (Pluggy/Belvo/Celcoin); gRPC/GraphQL/service mesh; native mobile apps; web dashboard (read-only dashboard belongs in Phase 2); MCP server exposure; self-hosted LLMs.