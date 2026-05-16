# Implementation Plan: Foundation — Walking Skeleton (Local + Cloud)

**Branch**: `001-foundation-skeleton` | **Date**: 2026-05-15 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `/specs/001-foundation-skeleton/spec.md`

## Summary

Build the **smallest end-to-end shape** of Luci that runs in both local and cloud environments from the same code and the same contracts, with no domain logic. After this feature lands, a new engineer can `git clone && make bootstrap && make up && make dev` and within 30 minutes observe both services healthy, talking over an authenticated M2M channel, and reporting database + cache status (SC-001). Switching to Supabase + a remote Redis is an env-var swap, not a code change (SC-004).

**Technical approach** (resolved in [`research.md`](research.md)):

- **Monorepo** with `agent/` (Python 3.12 + FastAPI, `uv`-managed) and `finance-api/` (Kotlin + Spring Boot 3.4 on JDK 21, Gradle wrapper). The committed `openapi.yaml` at the repo root governs the seam; `openapi-python-client` regenerates the Python client during `make bootstrap` so hand-written `httpx` to Spring is impossible (FR-002b, constitution III).
- **Local infra** via `infra/docker-compose.yml`: Postgres 15 + pgvector (`ghcr.io/tembo-io/pg-pgmq:pg15`) and Redis 7 (`redis:7-alpine`). MockServer/MinIO/Langfuse are deferred to feature-specific PRs. (R-10, R-11.)
- **M2M auth (RS256)** with `make bootstrap` generating a 2048-bit keypair on first run, idempotent on re-run. Spring validates via `spring-security-oauth2-resource-server` pointed at its own `/.well-known/jwks.json` (R-3, R-5, FR-019, FR-021).
- **`/health`** uses Spring Boot Actuator's `Health` schema (FR-024); Python mirrors the shape with a hand-written endpoint that does `SELECT 1` and Redis `PING`. Both services serve `/metrics` (Prometheus text format) at the same path via Actuator path-mapping on Spring and `prometheus_client` on Python (R-7, R-8, FR-026b).
- **Structured JSON logs** via `structlog` (Python) and `logstash-logback-encoder` (Spring) from day 0 (R-9, FR-026a).
- **Quality gates** wired into a single `pre-commit` config consumed by both `make lint` and `git commit`: ruff + ruff-format + mypy --strict (Python), ktlint via Gradle (Kotlin), gitleaks (whole tree). (R-14, FR-029–FR-031.)
- **Flyway baseline** is a two-statement migration: `CREATE EXTENSION IF NOT EXISTS vector` + a `COMMENT ON TABLE flyway_schema_history` marker. Verifiable from a query; leaves no schema residue (R-12, FR-014).

**OpenTelemetry tracing SDK initialization is explicitly deferred** to the first feature that introduces inter-service work beyond the `whoami` probe (FR-026d). Existing `trace_id` fields in JSON logs are populated from headers or generated locally; no tracer provider is wired by this feature.

## Technical Context

**Language/Version**:
- Python **3.12** (managed by `uv`).
- Kotlin **2.0.x** on JDK **21** (Temurin), Gradle Wrapper 8.x.

**Primary Dependencies**:
- *Python*: `fastapi`, `uvicorn[standard]`, `pydantic`, `pydantic-settings`, `httpx`, `pyjwt[crypto]`, `cryptography`, `psycopg[binary,pool]`, `redis>=5`, `structlog`, `prometheus-client`. Dev: `ruff`, `mypy`, `pytest`, `pytest-asyncio`, `respx`, `openapi-python-client`. (R-15.)
- *Spring*: `spring-boot-starter-web`, `-actuator`, `-data-redis`, `-jdbc`, `-security`, `-oauth2-resource-server`; `springdoc-openapi-starter-webmvc-ui`; `micrometer-registry-prometheus`; `logstash-logback-encoder`; `nimbus-jose-jwt`; `org.postgresql:postgresql`; `flyway-core` + `flyway-database-postgresql`. Test: `spring-boot-starter-test`, `testcontainers:postgresql`, `testcontainers:junit-jupiter`, `testcontainers-redis`. (R-16.)

**Storage**: Postgres 15 with pgvector — local via `ghcr.io/tembo-io/pg-pgmq:pg15`, cloud via Supabase. Redis 7 — local via `redis:7-alpine`, cloud via Upstash or equivalent. (R-11, FR-006, FR-012.)

**Testing**:
- *Python*: `pytest` + `pytest-asyncio` for async routes; `respx` for httpx mocks; the M2M round-trip test in `agent/tests/test_skeleton.py` exercises the **real** local Spring (no respx) because this feature's whole point is to validate the seam end to end.
- *Spring*: JUnit 5 + Spring Boot Test; Testcontainers (Postgres + pgvector + Redis) for integration tests of the JWT filter, Actuator health, and Flyway baseline.

**Target Platform**:
- Local: Linux/macOS developer machines running Docker Engine ≥ 24.
- Cloud: DigitalOcean App Platform (TDD §4.1, §4.2) — but the configuration surface is platform-agnostic (Assumption A-3 in spec).

**Project Type**: **Monorepo with two web services** plus shared contracts and infra. Real layout in §"Project Structure" below.

**Performance Goals**:
- `make bootstrap` → `make dev` → both `/health` UP within **30 min** wall-clock on a clean machine (SC-001).
- `/health` UP within **60 s** of `make dev` on 19 of 20 runs (SC-002).
- `make lint` ≤ **30 s** warm, ≤ **90 s** cold (SC-003).
- Stopping any required container surfaces as `DOWN` within **10 s** (SC-008, FR-025).

**Constraints**:
- No domain logic, no LLM calls, no prompts, no `@agent.tool`, no audit log, no rate limiter (FR-032, FR-033, FR-034).
- No source-code change required to swap profiles (FR-013, SC-004).
- No `Double`/`Float` for currency (constitution I) — N/A in this feature (no currency yet) but the linter wiring lands so day-2 PRs are pre-gated.
- All POSTs in this feature are read-only or non-mutating (`whoami` is a probe); `@Idempotent` enforcement (constitution V) is *scaffolded* in the lint stack but not exercised — no `Idempotency-Key` is required on the skeleton POST. The lint rule lands here so the first state-mutating PR is gated from line one.

**Scale/Scope**: Skeleton; not user-facing. Zero data rows in scope. The repository net adds the Make/compose/lint/codegen toolchain plus two service skeletons (≈ a dozen Python files, ≈ two dozen Kotlin files).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The seven gates below derive directly from constitution principles I–VII (v1.0.0, ratified 2026-05-15). Each gate states what would constitute a violation **in this feature** and the verdict — PRE-Phase-0 (initial) and POST-Phase-1 (after the design artefacts in this directory exist).

| # | Gate | Initial verdict | Post-design verdict |
|---|---|---|---|
| **I** | **Zero LLM arithmetic.** No LLM output schema may contain `balance`/`total`/`projection`/etc.; `Decimal` for currency; CI blocks `float`/`Double`/`Float`/`Number` for currency. | **N/A — no LLM, no currency in this feature.** FR-032/-033/-034 ban every surface this gate guards. | **PASS unchanged.** Phase-1 artefacts (`openapi.yaml`, `data-model.md`) declare no monetary field; the schemas surfaced are auth claims, health probes, and JWK keys. |
| **II** | **LGPD lifecycle.** Soft-delete + 15-day hard-purge + audit pseudonymization. | **N/A — no user records, no PII, no audit writes.** | **PASS unchanged.** Phase-1 entities are all infrastructure shapes (Workspace Profile, Service Health Report, M2M Token, JWKS, baseline migration) and explicitly carry no PII. |
| **III** | **Contract first.** Single `openapi.yaml` at repo root; springdoc parity; generated Python client; no hand-written `httpx` to Spring; PR touching both services must touch the contract. | **PASS (committed direction).** Plan explicitly carries the OpenAPI artefact and the generated-client wiring (R-6, FR-002b). | **PASS strengthened.** Phase-1 produced `contracts/openapi.yaml` documenting `whoami` + `/health` + JWKS; `make codegen` is committed in the quickstart; the constitution's anti-hand-written-httpx rule is referenced in research R-6 as the active enforcement. |
| **IV** | **Test-first & eval-gated.** Failing test precedes implementation; Testcontainers for Spring, respx + recorded cassettes for Python; eval gates on prompt/tool/schema changes. | **Partial — applies.** The `speckit.superb.tdd` mandatory hook fires before `/speckit-implement` so the failing-test discipline is binding for this feature. The eval gate is **N/A** here because nothing in scope touches `agent/orchestrator/prompts/`, `tools.py`, `schemas.py`, `output_validators.py`, or `fewshots/` (FR-033, FR-034). | **PASS unchanged.** Phase-1 quickstart names `make verify-m2m` (an integration test invoked from `agent/tests/test_skeleton.py`) and the Spring integration tests under Testcontainers as the failing-tests-first surface. Eval scaffolding (empty `agent/tests/fixtures/llm/`) is in scope per spec A-9; no datasets land. |
| **V** | **Idempotency, audit & observability from day 1.** Every state-mutating POST → `Idempotency-Key`; every `@agent.tool` → audit-logged; three pillars live; cost kill-switch. | **Partial — observability scaffolding only.** Day-1 surfaces in scope: structured JSON logs (FR-026a) + Prometheus `/metrics` (FR-026b). **Audit log** (no `@agent.tool` exists yet) and **cost kill-switch** (no LLM call exists yet) are out of scope. **Idempotency** lint rule lands in this feature so the first state-mutating PR is gated from line one; the skeleton's only POST (`/v1/internal/whoami`) is a read-only probe and is explicitly out of the idempotency surface. **OpenTelemetry tracing SDK initialization is deferred** (spec clarification, FR-026d) to the first feature with non-probe inter-service work. | **PASS unchanged.** Phase-1 contract `openapi.yaml` marks `whoami` as a probe (`securitySchemes: m2mJwt`, no `Idempotency-Key` parameter); `data-model.md` §3 documents the Actuator-shape `HealthReport`; `data-model.md` §4 documents the four distinct JWT-failure log markers FR-022 requires. |
| **VI** | **Prompts, schemas & migrations are code.** Prompts versioned in `prompts/system_v{N}.md` with `current.md` symlink and `# FROZEN` discipline; user input delimited; tool surface static; Flyway-only schema; additive migrations. | **Partial — applies to migrations only.** No prompt or output-schema surface in scope. The Flyway baseline `V1__baseline.sql` is *additive by construction* (one extension, one comment). | **PASS unchanged.** Phase-1 `data-model.md` §6 specifies the migration contents and verification query; an empty `agent/orchestrator/prompts/` scaffold lands per FR-033 but no `system_v1.md` and no `current.md` symlink are created (would trip the FROZEN logic unnecessarily). |
| **VII** | **Tiered, tool-agnostic AI-assisted development.** T1 owns contracts/migrations/prompts/`schemas.py`/ADRs; T2 writes conforming code; cross-tier reassignment needs ADR. | **PASS by design.** This feature *is* a T1 + T2 task: T1 produces `openapi.yaml` and the Flyway baseline; T2 produces controllers, services, tests, and infra glue. The split is intrinsic. | **PASS unchanged.** Phase-1 artefacts are T1; no T1 surface is touched outside this feature's scope. |

**Initial verdict (pre-research)**: **PASS.** Three gates are N/A by construction (I, II), one is genuinely partial (V, where day-1-but-only-the-foundations is the spec's explicit posture), and the rest are PASS.

**Post-design verdict (after Phase 1)**: **PASS, unchanged.** The Phase 1 artefacts (`research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`) do not introduce any surface that would flip any gate from PASS/Partial/N/A to FAIL. No Complexity Tracking entries are needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation-skeleton/
├── plan.md                # This file (/speckit-plan output)
├── research.md            # Phase 0 output — 18 decisions resolving deferred unknowns
├── data-model.md          # Phase 1 output — infrastructure-level entities
├── quickstart.md          # Phase 1 output — git-clone-to-green runbook
├── contracts/
│   └── openapi.yaml       # Phase 1 output — copied to repo-root /openapi.yaml at implement time
├── checklists/
│   └── constitution.md    # Pre-existing (constitution self-audit)
├── spec.md                # /speckit-specify output (already on disk)
└── tasks.md               # Phase 2 output — generated by /speckit-tasks (NOT this command)
```

### Source Code (repository root, post-implement)

```text
luci/
├── agent/                                      # T2 surface (Python)
│   ├── main.py                                 # FastAPI app factory; mounts /health, /metrics, webhook stubs
│   ├── config/
│   │   └── settings.py                         # pydantic-settings reading .env / process env (FR-010)
│   ├── observability/
│   │   ├── logging.py                          # structlog JSON config (FR-026a, R-9)
│   │   └── metrics.py                          # prometheus_client middleware (FR-026b, R-8)
│   ├── health/
│   │   └── routes.py                           # GET /health → DB + Redis probes (FR-023..FR-026, R-7)
│   ├── http/
│   │   ├── m2m_auth.py                         # Mints RS256 JWTs via PyJWT (R-4, FR-017)
│   │   └── spring_client.py                    # Thin wrapper around the generated client (FR-002b)
│   ├── finance_api_client/                     # Generated by openapi-python-client (R-6)
│   ├── orchestrator/
│   │   ├── prompts/                            # Empty scaffold (FR-033 — no system_v1.md)
│   │   └── __init__.py
│   ├── tests/
│   │   ├── fixtures/llm/                       # Empty scaffold per spec A-9
│   │   ├── test_skeleton.py                    # whoami round-trip; /health DB+Redis probes
│   │   └── conftest.py
│   ├── pyproject.toml                          # uv-managed, ruff + mypy config (R-15)
│   └── .python-version                         # 3.12
│
├── finance-api/                                # T2 surface (Kotlin/Spring)
│   ├── src/main/kotlin/app/luci/finance/
│   │   ├── FinanceApiApplication.kt
│   │   ├── api/
│   │   │   ├── WhoamiController.kt             # POST /v1/internal/whoami (FR-020)
│   │   │   ├── JwksController.kt               # GET /.well-known/jwks.json (FR-019)
│   │   │   └── advice/GlobalExceptionHandler.kt# No raw stack traces (constitution anti-vibe)
│   │   ├── config/
│   │   │   ├── SecurityConfig.kt               # Resource server + path matchers (R-3, R-17)
│   │   │   ├── OpenApiConfig.kt                # springdoc bean wiring (FR-002a)
│   │   │   └── M2MKeyConfig.kt                 # Loads LUCI_M2M_PUBLIC_KEY_PEM into a JWKSet
│   │   └── observability/
│   │       └── LoggingConfig.kt                # Optional bean for MDC propagation
│   ├── src/main/resources/
│   │   ├── application.yml                     # Common defaults (Actuator paths, JWT iss/aud)
│   │   ├── application-local.yml               # localhost URLs
│   │   ├── application-prod.yml                # env-var placeholders (FR-009, R-17)
│   │   ├── logback-spring.xml                  # logstash-logback-encoder JSON output (R-9)
│   │   └── db/migration/
│   │       └── V1__baseline.sql                # CREATE EXTENSION + COMMENT marker (FR-014, R-12)
│   ├── src/test/kotlin/app/luci/finance/
│   │   ├── WhoamiIntegrationTest.kt            # Testcontainers Postgres + Redis (FR-018, FR-020)
│   │   ├── HealthEndpointIntegrationTest.kt    # Stops Redis, asserts DOWN within ≤10s (FR-025)
│   │   └── FlywayBaselineTest.kt               # Verifies the marker comment exists post-migrate
│   ├── build.gradle.kts                        # Kotlin DSL; ktlint, springdoc, JaCoCo (R-16)
│   ├── settings.gradle.kts
│   ├── gradlew, gradlew.bat, gradle/wrapper/   # committed wrapper
│
├── openapi.yaml                                # T1 surface — copied from specs/.../contracts/openapi.yaml
│
├── infra/
│   └── docker-compose.yml                      # Postgres + Redis with luci_* container names (R-10, R-11)
│
├── scripts/
│   └── bootstrap-keys.sh                       # Generates LUCI_M2M_*_PEM into .env.local (R-5, FR-021)
│
├── Makefile                                    # bootstrap, up, dev, lint, codegen, reset, verify-m2m (R-13)
├── .env.example                                # Manifest of every env var (FR-011, R-18)
├── .gitignore                                  # excludes .env.local, build artefacts
├── .pre-commit-config.yaml                     # ruff, mypy, ktlint, gitleaks (R-14, FR-029)
├── pyproject.toml                              # Root-level marker for repo-wide tools (ruff config can live here)
├── ruff.toml                                   # OR config in pyproject.toml — single source of truth
├── mypy.ini                                    # strict = True; disallow_any_explicit = True
├── .gitleaks.toml                              # Allowlist + rules
├── .editorconfig
│
├── docs/                                       # existing — PRD, TDD, ADRs (not modified by this feature)
├── .specify/                                   # existing — Spec Kit workflow
└── .claude/                                    # existing — Claude Code skill packs
```

**Structure Decision**: Monorepo at repo root with two top-level service directories (`agent/`, `finance-api/`), one contract seam (`openapi.yaml`), one infra directory (`infra/`), and a single Makefile delegating to native per-service tooling (FR-003, FR-004). The structure mirrors TDD §17.3 verbatim; everything declared above already has either an FR or a research-decision (R-x) entry justifying it.

The empty scaffolds (`agent/orchestrator/prompts/`, `agent/tests/fixtures/llm/`) are deliberate per FR-033 / spec A-9 — they reserve the directory shape so downstream features add files instead of restructuring.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified.**

**Empty.** No constitution gate is violated by this plan. The "Partial" verdicts on gates IV, V, and VI are not violations — they are the explicit, spec-clarified posture of a walking skeleton (FR-026d on tracing; FR-033 on prompts; the absence of `@agent.tool` and state-mutating writes from FR-032). Each Partial verdict is documented in the gate's row above with the FR(s) that authorise it.

## Post-`/speckit-plan` next steps

This plan terminates at the end of Phase 1 (per the speckit-plan workflow). The next command in the chain is `/speckit-tasks`, which will read this plan plus `research.md`, `data-model.md`, and `contracts/openapi.yaml` and produce `tasks.md` — the ordered, dependency-aware task list. Suggested follow-up sequence:

1. `/speckit-tasks` — generate `tasks.md`.
2. `/speckit-superb-review` — audit task coverage against the spec's 32 FRs and 9 SCs (the spec's surface is large for a "skeleton"; a coverage pass is warranted).
3. `/speckit-superb-tdd` (mandatory hook) — enforce failing-test-first before implementation.
4. `/speckit-implement` — execute.
