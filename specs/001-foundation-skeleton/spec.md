# Feature Specification: Foundation — Walking Skeleton (Local + Cloud)

**Feature Branch**: `001-foundation-skeleton`

**Created**: 2026-05-15

**Status**: Verified

**Input**: User description: "Phase 1 - Foundation (Walking Skeleton: Local + Cloud). Build the monorepo and the hybrid (local + cloud) infrastructure that supports both isolated local development and remote deploy. Out of scope: business rules. In scope: monorepo layout (`agent/` Python, `finance-api/` Kotlin/Spring), local docker-compose (Postgres+pgvector, Redis), cloud env-var foundation (Supabase + remote Redis), Spring profiles (`application-local.yml`, `application-prod.yml`), Python `.env`/`.env.example`, Flyway baseline migration, M2M JWT (RS256) foundation, `/health` endpoints on both services exposing DB + Cache status, Ruff + ktlint, and a root `Makefile` with utility targets (`make up`, `make lint`, …)."

## Overview

This feature delivers the **walking skeleton** of Luci: the smallest end-to-end shape of the system that **runs in both local and cloud environments using the same code and the same contracts**, with no domain logic.

After this feature lands, a new developer can clone the repository, run `make bootstrap && make up && make dev`, and observe both services healthy, talking to each other over an authenticated M2M channel, and reporting database + cache status — without writing any feature code. Switching the same code to run against Supabase (cloud Postgres) and a remote Redis is a profile/env-var swap, not a code change.

This feature is the substrate that every subsequent feature (intent parsing, transaction ingest, billing, etc.) is layered onto. Its job is to make the orchestrator–worker split (per constitution §III) reachable from `git clone` in under 30 minutes and to make environment parity verifiable on Day 1.

## Clarifications

### Session 2026-05-15

- Q: Which observability pillars must the walking-skeleton scaffold from day 1, given constitution §V mandates "structured JSON logs + Prometheus + OTel traces"? → A: Partial — structured JSON logger config and a `/metrics` (Prometheus) endpoint on both services land now; OTel tracing SDK initialization is deferred to the first feature that introduces inter-service calls beyond the `whoami` probe. Rationale: logs + `/metrics` are cheap and required to make the skeleton's own behavior (auth rejections, `/health` outcomes) observable; an inert OTel SDK init would create churn without value until there is something meaningful to trace.
- Q: How is the `openapi.yaml` contract seam populated for the skeleton, and how does the Python agent call Spring's `whoami` without violating the "no hand-written httpx to Spring" lint rule? → A: `openapi.yaml` documents `POST /v1/internal/whoami`, `GET /health` (both services), and `GET /.well-known/jwks.json` from day 1; the Python agent's `whoami` call goes through a client produced by `openapi-python-client`; the anti-hand-written-httpx lint rule is enforced with no exemptions. Rationale: honors contract-first (constitution §III) with no special-casing, proves the codegen pipeline before there is domain logic depending on it, and keeps the auth-seam test a real exercise of the contract seam.
- Q: Should `make bootstrap` auto-generate the local M2M RS256 keypair, or is key provisioning a separate manual step? → A: `make bootstrap` MUST generate a 2048-bit RS256 keypair on first run, persist the private half to `.env.local` (gitignored), and write the public half where Spring reads it. The target MUST be idempotent on re-runs (existing keys preserved). Rationale: keeps `git clone → make bootstrap → make up → make dev` a true single onramp and protects SC-001 (≤ 30 min to a green local stack).
- Q: Should `/health` return a strictly shared JSON schema across both services, or may each service emit its own shape? → A: Spring exposes the default Spring Boot Actuator `/health` JSON shape (top-level `status: UP|DOWN` plus a `components` map keyed by dependency name — at minimum `db` and `redis` — each with its own `status`). The Python agent's `/health` mirrors that exact shape by hand. `openapi.yaml` documents `/health` using the Actuator shape as the contracted schema (rather than introducing a Luci-custom schema). Rationale: reuses Actuator's mature health-indicator machinery out of the box on Spring, gives the Python side a precise, scrapable target shape, and avoids inventing a parallel HealthReport contract for a skeleton endpoint.
- Q: In the `prod` profile, who can reach `GET /.well-known/jwks.json` on Spring? → A: Internal-only — JWKS is reachable in `prod` only from the platform-internal network or behind the same allow-list that gates `/v1/internal/*`. It MUST NOT be reachable unauthenticated from the public internet. Rationale: Python (the only consumer in this feature) lives on the internal network; minimising attack surface costs nothing and matches the gating posture already applied to `whoami`. If a future external consumer needs JWKS, a deliberate ADR-recorded relaxation can re-expose it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Day-1 local bootstrap (Priority: P1)

A new engineer joins the team. They clone the repository, run three commands, and within 30 minutes of `git clone` they have both services running locally against a real Postgres (with `pgvector` extension) and a real Redis container — and both `/health` endpoints return green.

**Why this priority**: Without this, every other downstream feature is blocked. It is the smallest slice that proves the orchestrator–worker topology, the monorepo layout, and the contract seam are physically reachable. It is the prerequisite for the TDD gate (Principle IV) — there is nowhere to run a failing test against until this is in place.

**Independent Test**: On a clean machine with only Docker, JDK 21, and Python 3.12 installed, run `git clone <repo> && cd luci && make bootstrap && make up && make dev`. Within 30 minutes, both `curl localhost:8080/health` (Spring) and `curl localhost:8000/health` (Python) MUST return `200 OK` with body indicating `database: UP` and `cache: UP`.

**Acceptance Scenarios**:

1. **Given** a clean machine with prerequisites installed and no prior state, **When** the developer runs `make bootstrap`, **Then** `uv` (Python) and the Gradle wrapper (JVM) are available, `.env.example` is copied to `.env.local`, and pre-commit hooks (ruff, ktlint, gitleaks) are installed.
2. **Given** a successful `make bootstrap`, **When** the developer runs `make up`, **Then** the compose stack reaches a healthy state (Postgres with `pgvector`, Redis, plus any additional ancillary containers declared by the plan), Flyway applies the baseline migration once, and the command exits 0.
3. **Given** `make up` has completed, **When** the developer runs `make dev`, **Then** Spring Boot (port 8080) and the Python agent (port 8000) start with live-reload enabled and both `/health` endpoints respond `200 OK` within 60 seconds.
4. **Given** both services are running locally, **When** any required infrastructure container is stopped (`docker stop luci_postgres` or `docker stop luci_redis`), **Then** the affected service's `/health` endpoint reports the failing dependency as `DOWN` within one health-check cycle and returns a non-2xx status — the service does not silently mask the failure.
5. **Given** a developer modifies `agent/orchestrator/agent.py` (Python) or any Kotlin source under `finance-api/src/main/kotlin/`, **When** they save the file, **Then** the corresponding service reloads automatically without manual restart.

---

### User Story 2 — Authenticated service-to-service channel (Priority: P2)

The Python Agent issues a signed M2M call to the Spring Boot Finance API and receives a successful, identity-aware response. An unsigned or expired call is rejected before reaching any controller. This proves the contract-first seam (constitution §III) and the M2M JWT auth posture (TDD §8.2) — not by exercising a domain endpoint, but by exercising a no-op probe endpoint owned by the Finance API specifically for skeleton validation (e.g., `POST /v1/internal/whoami`).

**Why this priority**: The constitution forbids user-JWT impersonation between services (§Architectural Constraints) and mandates contract-first integration. The two services need a verified channel before any tool call is wired. This story is independently testable: a request that passes the M2M auth gate is the meaningful slice of value, regardless of what the endpoint returns.

**Independent Test**: With local services running, an integration test in `agent/tests/test_skeleton.py` issues an `httpx` POST to `/v1/internal/whoami` carrying a freshly minted RS256 JWT. Spring MUST respond `200 OK` with the verified `subject`, `user_id`, and `intent` claims echoed back. The same test repeated with no `Authorization` header MUST receive `401`. The same test with a JWT signed by the wrong key MUST receive `401`.

**Acceptance Scenarios**:

1. **Given** the M2M signing keypair is provisioned (locally generated for dev, env-injected for cloud) and both services are running, **When** the Python agent calls `/v1/internal/whoami` with a valid RS256 JWT (issuer `python-agent.luci.app`, audience `finance-api.luci.app`, subject `service:python-agent`, TTL 15 min), **Then** Spring returns `200 OK` and echoes back the verified claims.
2. **Given** both services are running, **When** a request is made to `/v1/internal/whoami` with no `Authorization` header, **Then** Spring returns `401` with no stack trace and no body content beyond a generic error code.
3. **Given** both services are running, **When** a request is made with a JWT signed by an unknown key, an expired JWT (`exp` in the past), or a JWT whose `aud` is not `finance-api.luci.app`, **Then** Spring returns `401` and records the rejection in the structured log (per Principle V).
4. **Given** the JWKS endpoint is configured, **When** a client fetches `GET /.well-known/jwks.json` from Spring, **Then** they receive the public key set used for verification, served as JSON with the correct content type.

---

### User Story 3 — Cloud parity via profile + env swap (Priority: P3)

The same artifacts that ran locally in Story 1 also run against Supabase Postgres and a remote Redis (e.g., Upstash) by switching only environment configuration — no code change, no rebuild logic change. Both `/health` endpoints report `database: UP` and `cache: UP` when pointed at the remote dependencies, and the Flyway baseline migration applies cleanly to the remote Postgres on first deploy.

**Why this priority**: This is what makes the system *cloud-ready* on Day 1 instead of cloud-retrofitted on Day 90. It surfaces — early — any local/cloud divergence in connection strings, TLS handling, IP allow-lists, or pgvector extension availability. It does not require an actual production deploy; it only requires that the configuration surface for `production` is exercised and proven inert. Note: actual production deployments MUST observe the deploy-ordering invariant from Constitution §III (Spring deploys before Python on additive contract changes; destructive changes require a two-release deprecation window). This spec defers CI/CD pipeline enforcement of that invariant to a follow-on feature, but the invariant itself applies from Day 1.

**Independent Test**: An engineer points `SPRING_PROFILES_ACTIVE=prod` and the equivalent Python env at a Supabase project + a remote Redis instance using only the documented environment variables, runs both services locally (still on their laptop, but talking to remote infra), and both `/health` endpoints return `200 OK` with `database: UP` and `cache: UP`. The Flyway baseline migration MUST apply once and the second run MUST be a no-op (idempotent).

**Acceptance Scenarios**:

1. **Given** a fresh Supabase project with `pgvector` extension available and a fresh remote Redis instance, **When** the engineer sets the documented `LUCI_DB_*` and `LUCI_REDIS_*` environment variables and starts Spring with profile `prod`, **Then** Spring boots successfully, Flyway applies the baseline migration, and `/health` reports `database: UP`, `cache: UP`.
2. **Given** the same Supabase project after the baseline migration has been applied, **When** Spring is restarted, **Then** Flyway detects no pending migrations and the service boots without re-running the migration.
3. **Given** the Python agent is configured via `.env.local` for cloud (`SPRING_BASE_URL=https://...`, `LUCI_ENV=prod`), **When** the agent calls Spring's `/v1/internal/whoami`, **Then** the M2M JWT is accepted and the round-trip succeeds over TLS.
4. **Given** any required environment variable is missing or malformed, **When** either service starts, **Then** it fails fast at boot with a single human-readable error naming the missing variable — it does NOT start in a degraded state, and it does NOT silently fall back to a default that would hide the misconfiguration.
5. **Given** the same code commit, **When** the developer switches `SPRING_PROFILES_ACTIVE` between `local` and `prod` (and adjusts the env file accordingly), **Then** no source-code changes are required to retarget Postgres and Redis.

---

### User Story 4 — Day-0 quality gates run locally and in pre-commit (Priority: P4)

The developer runs `make lint` and gets a deterministic pass/fail across both languages: `ruff check` + `mypy --strict` on `agent/`, `ktlint` on `finance-api/`, plus `gitleaks` over the working tree. The same checks fire on `git commit` via pre-commit, so a violation cannot reach a PR by accident.

**Why this priority**: The constitution (§Architectural Constraints — anti-vibe rules) and TDD §13.2 bind these checks. Wiring them on Day 0 — before there is any domain code to lint — costs nothing now and prevents the entire class of "we'll turn on strict mode later" debt. Without this, Principle IV (Test-First & Eval-Gated Delivery) cannot be enforced because the gate machinery does not exist yet.

**Independent Test**: Introduce a deliberate violation in each surface — a `print()` call in `agent/`, a `Double` for currency in `finance-api/`, and a fake AKIA-prefixed string in any file. Running `make lint` MUST fail with three distinct, attributed violations and a non-zero exit code. Removing the violations MUST make `make lint` pass.

**Acceptance Scenarios**:

1. **Given** clean working tree, **When** the developer runs `make lint`, **Then** ruff, mypy (strict), ktlint, and gitleaks all run and the command exits 0 in under 30 seconds on a warm cache.
2. **Given** a deliberate ruff violation introduced in `agent/`, **When** the developer attempts `git commit`, **Then** the pre-commit hook blocks the commit and prints the ruff diagnostic.
3. **Given** a deliberate ktlint violation in `finance-api/`, **When** the developer runs `make lint`, **Then** ktlint reports the violation with file path and line number and `make lint` exits non-zero.
4. **Given** a high-entropy secret-shaped string is staged, **When** `git commit` runs, **Then** the gitleaks hook blocks the commit and identifies the offending pattern.

---

### Edge Cases

- **`pgvector` extension not available on the local Postgres image**: `make up` MUST fail fast with a clear error pointing to the docker image variant required; absence is detected at migration time, not silently masked at runtime.
- **Developer runs `make up` with a stale volume from a prior project on the same machine**: `make reset` MUST exist and MUST wipe volumes deterministically before re-running `make up`; the failure mode of a poisoned volume MUST surface as a Flyway validation error, never as a silent data mismatch.
- **Ports 5432 / 6379 / 8080 / 8000 already bound** on the developer's machine: `make up` (and `make dev`) MUST emit a human-readable error naming the conflicting port and the container or service that wanted it. (Default-port collision is a known DevX papercut on Brazilian dev machines that already run a local Postgres.)
- **Two developers on the same shared box racing on Docker container names**: container names MUST be prefixed (`luci_*`) so they do not collide with arbitrary other projects.
- **Cloud Postgres reachable but `pgvector` extension not yet enabled on the Supabase project**: the baseline Flyway migration MUST execute `CREATE EXTENSION IF NOT EXISTS vector` and MUST fail loudly with the operator-actionable error if the role lacks permission.
- **Cloud Redis with TLS-required connection but profile config assumes plaintext**: the connection MUST fail at startup with a single boot-time error, never at the first cache write.
- **JWT signing key not configured in `prod` profile**: the service MUST refuse to start; it MUST NOT fall back to a development key.
- **Clock skew between the Python agent and the Spring service exceeds the JWT TTL (15 min)**: Spring's JWT validation MUST tolerate a small leeway (≤ 60 s); skew beyond that MUST produce a 401 with a log entry distinct from "expired token".
- **Pre-commit hook environment differs from CI environment**: `make lint` and any future CI pipeline MUST run the same hook configuration, sourced from a single committed file — divergence is a contract drift signal.

## Requirements *(mandatory)*

### Functional Requirements — Monorepo & Layout

- **FR-001**: The repository MUST host both services in a single working tree, with `agent/` for the Python service and `finance-api/` for the Kotlin/Spring service, at the monorepo root. The layout MUST match the structure declared in TDD §17.3.
- **FR-002**: The repository MUST contain a single `openapi.yaml` at the root reserved as the contract seam between the two services. For this feature, `openapi.yaml` MUST be a valid OpenAPI 3.x document and MUST document the three skeleton-level endpoints: `POST /v1/internal/whoami` (with its M2M-JWT-claim echo schema); `GET /health` on both services, using the Spring Boot Actuator JSON shape — top-level `status: UP|DOWN` plus a `components` map keyed by dependency name (at minimum `db` and `redis`), each with its own `status` (see FR-024) — as the contracted schema (rather than a custom Luci HealthReport); and `GET /.well-known/jwks.json` (with the JWKS schema). No domain endpoints beyond these are introduced by this feature.
- **FR-002a**: Spring MUST serve its actual OpenAPI document (via springdoc-openapi or equivalent) at a documented path, and the served document MUST match the committed `openapi.yaml` for the endpoints listed in FR-002. A CI check enforcing this parity may be added in a later feature; the structural prerequisite (springdoc wired, endpoint paths and schemas matching) MUST be satisfied now.
- **FR-002b**: The Python agent's call to Spring's `/v1/internal/whoami` (and any other Spring endpoint introduced later) MUST go through a client generated from `openapi.yaml` by `openapi-python-client`. Hand-written `httpx` calls to Spring are forbidden by the existing constitutional anti-vibe rule; this feature MUST NOT introduce any exemption to that rule. `make bootstrap` (or an equivalent committed target invoked by it) MUST regenerate the Python client from the committed `openapi.yaml` so the generated artifact is reproducible and never drifts silently.
- **FR-003**: Each service directory MUST be independently buildable from its own root using its native tooling — `uv` / `pyproject.toml` for `agent/`, Gradle wrapper for `finance-api/` — without requiring shell scripts that reach across service boundaries for build steps.
- **FR-004**: A single `Makefile` at the repository root MUST be the canonical entry point for cross-service utility commands (`make bootstrap`, `make up`, `make dev`, `make lint`, `make reset`). The Makefile MUST delegate to per-service tooling rather than reimplementing it.

### Functional Requirements — Local Infrastructure

- **FR-005**: A `docker-compose` definition MUST be present and runnable from the repository root via `make up`. (See Assumptions §A-1 for the exact file location.)
- **FR-006**: The compose definition MUST start, at minimum: (a) Postgres 15 with the `pgvector` extension available; (b) Redis 7 with append-only persistence enabled. It MAY additionally start MockServer (Mercado Pago stub) and MinIO (S3-compatible object storage) as documented in TDD §18.1, but neither is required for Story 1 to pass.
- **FR-007**: Each container in the compose definition MUST declare a healthcheck. `make up` MUST NOT exit successfully until all required containers report healthy.
- **FR-008**: A `make reset` target MUST exist that fully wipes the local stack (volumes included) and re-runs `make up`, so that recovery from a poisoned local state is a single deterministic command.

### Functional Requirements — Cloud Configuration Surface

- **FR-009**: Spring Boot MUST expose at least two profiles: `local` (binds to localhost Postgres + Redis from the compose stack) and `prod` (binds to remote Postgres + remote Redis via environment variables). The profile MUST be selected via `SPRING_PROFILES_ACTIVE` with no source change required.
- **FR-010**: The Python agent MUST source all environment-specific configuration through a single mechanism (`pydantic-settings` reading from `.env` and process environment), MUST refuse to start when a required variable is absent, and MUST NOT use silent defaults for any value that differs between local and cloud.
- **FR-011**: A `.env.example` file MUST list every environment variable consumed by either service, with safe placeholder values, grouped by domain (database, cache, M2M auth, telemetry, external services), and MUST be the only authoritative reference developers need. `.env.local` MUST be `.gitignored` and MUST be created by `make bootstrap`.
- **FR-012**: The cloud configuration MUST accept Supabase Postgres connection strings (including TLS-required mode and IP-allow-listed egress) and MUST accept a remote Redis URL (TLS-required mode supported); no assumption of a specific remote Redis vendor MUST be hard-coded.
- **FR-013**: A single source commit MUST be able to run against either environment by changing only env vars and the Spring profile. Switching environments MUST NOT require code edits.

### Functional Requirements — Schema Migration

- **FR-014**: Database schema MUST be owned by Flyway, located at `finance-api/src/main/resources/db/migration/`. The baseline migration (`V1__baseline.sql`) MUST: (a) enable `pgvector` via `CREATE EXTENSION IF NOT EXISTS vector`; (b) install a small, identifiable artifact (e.g., a `_skeleton_marker` table, a no-op stored function, or a comment on `flyway_schema_history`) so the migration's effect is verifiable from a query; (c) contain NO domain tables (e.g., `users`, `transactions`) and NO currency column types (`DECIMAL(14,2)`, `DECIMAL(18,4)`) — the migration is strictly limited to infrastructure scaffolding and a reviewer MUST reject any PR that adds a domain table or currency column to `V1__baseline.sql`; (d) be additive only — `V1__baseline.sql` MUST NOT use `DROP COLUMN`, type-narrowing, or any DDL that requires a two-release deprecation window per Constitution §VI.
- **FR-015**: The baseline migration MUST apply identically against the local Postgres (Story 1) and the remote Postgres (Story 3). Edits to the schema outside Flyway (e.g., via Supabase Studio) MUST be considered drift. This spec does not yet introduce the CI check for drift, but the migration file structure MUST permit such a check to be added later without restructuring.
- **FR-016**: Re-running Spring against an already-migrated database MUST be a no-op (Flyway versioning is the source of truth); the service MUST NOT attempt to re-apply or rewrite a checksum-matched migration.

### Functional Requirements — Inter-Service Auth (M2M Foundation)

- **FR-017**: The Python agent MUST sign every outbound call to Spring with an RS256 JWT bearing `iss`, `aud`, `sub`, `exp` (TTL ≤ 15 min), and the project-defined custom claims (`user_id`, `intent`, `trace_id`), per TDD §8.2.
- **FR-018**: Spring MUST verify every inbound call to `/v1/internal/*` and `/v1/users/**` using the public half of the signing keypair; verification MUST be enforced by a filter that runs before any controller method body. Unsigned or invalid requests MUST be rejected with `401` and MUST NOT leak stack traces (per Principle V and constitution Architectural Constraints).
- **FR-019**: Spring MUST expose a `GET /.well-known/jwks.json` endpoint returning the active public key(s). The endpoint MUST be unauthenticated (no JWT required, since it publishes the verification keys). In the `local` profile it MUST be reachable on localhost. In the `prod` profile it MUST be reachable only from the platform-internal network or behind the same allow-list / `@Profile`-style gating used for `/v1/internal/*` (FR-020); it MUST NOT be reachable from the public internet without a deliberate, ADR-recorded relaxation. Spring's own JWT verifier MUST consume the JWKS over the internal path.
- **FR-020**: A no-op probe endpoint (`POST /v1/internal/whoami`) MUST exist on Spring that, when called with a valid M2M JWT, returns the verified `subject`, `user_id`, `intent`, and `trace_id` claims. This endpoint exists ONLY to validate the auth seam; it MUST NOT perform business logic and MUST NOT be reachable in the cloud `prod` profile from the public internet (network-level allowlist or `@Profile("local")`-style gating is acceptable).
- **FR-021**: The signing private key MUST be sourced from an environment variable (PEM-encoded) in both local and cloud, MUST NOT be committed to the repository, and MUST differ between local development and cloud. `make bootstrap` MUST generate a 2048-bit RS256 keypair on first run, persist the PEM-encoded private half to `.env.local` (gitignored), and place the PEM-encoded public half where Spring's local profile reads it (env var or a gitignored file under the developer's working tree). `make bootstrap` MUST be idempotent: on re-run, if a valid keypair is already present, it MUST be preserved (not regenerated). In `prod`, both halves are injected from the secrets manager — `make bootstrap` MUST NOT touch cloud keys.
- **FR-022**: JWT validation MUST allow a clock-skew leeway of at most 60 seconds and MUST log distinct events for the failure classes "missing token", "invalid signature", "expired token", and "audience mismatch".

### Functional Requirements — Health Reporting

- **FR-023**: Both services MUST expose an HTTP `GET /health` endpoint that returns `200 OK` when all critical dependencies are reachable and a non-2xx status when any critical dependency is unreachable. The endpoint MUST be unauthenticated and MUST NOT require an M2M JWT.
- **FR-024**: The `/health` response body on both services MUST follow the Spring Boot Actuator JSON shape: a top-level `status` field with value `UP` or `DOWN`, plus a `components` map keyed by dependency name. The `components` map MUST contain at least `db` and `redis`, each with its own `status: UP|DOWN`. Spring MUST achieve this by exposing Actuator's health endpoint at `/health` (via `management.endpoints.web.base-path` / `management.endpoints.web.path-mapping`) and enabling the built-in DataSource and Redis health indicators. The Python agent's `/health` MUST emit the same JSON shape (top-level `status` + `components.db` + `components.redis`), produced by a hand-written endpoint that probes Postgres and Redis directly. Either service MAY include additional fields (e.g., `details`, build SHA, profile name, uptime) but those are not required. The top-level `status` MUST be `DOWN` whenever any required component is `DOWN`.
- **FR-025**: The `/health` endpoint MUST NOT cache its result for longer than 10 seconds; a freshly-stopped dependency MUST surface as `DOWN` on the next health-check cycle, not on a manual restart.
- **FR-026**: `/health` MUST NOT itself perform any LLM call, any third-party API call, or any database write. The check is a connectivity probe, not a synthetic transaction.

### Functional Requirements — Observability Scaffolding (Day-0)

- **FR-026a**: Both services MUST emit logs as **structured JSON** (one event per line, UTC ISO-8601 timestamp, `level`, `logger`, `message`, plus contextual fields where present such as `trace_id`, `user_id`, `intent`, `service`, `profile`). All log events MUST be structured and field-tagged (not free-text) so that a downstream `PiiRedactor` filter can reliably identify and redact PII-bearing fields without parsing prose; this is a forward-compatibility requirement from Constitution §II. The Python agent MUST use a structured-logger configuration (e.g., `structlog` or `python-json-logger`); Spring MUST emit Logback output in JSON via `logstash-logback-encoder` or equivalent. `print()` and unstructured `System.out.println` are forbidden by the existing anti-vibe lint rules (FR-027/FR-028).
- **FR-026b**: Both services MUST expose a Prometheus-compatible scrape endpoint (`GET /metrics` for Python; `GET /actuator/prometheus` — or `GET /metrics` exposed via Spring Actuator's Prometheus registry — for Spring). The endpoint MUST be unauthenticated, MUST return the standard Prometheus text-format exposition, and MUST include at minimum process/runtime metrics plus an HTTP-request-count counter labeled by route, method, and status. No custom domain metrics are required by this feature.
- **FR-026c**: The `/metrics` endpoint MUST be reachable in the `local` profile from `localhost`. In `prod`, it MUST be reachable from the platform's scrape network (acceptable forms: internal-network only, allow-listed, or behind a sidecar) — it MUST NOT be exposed unauthenticated to the public internet. Exact production network gating is a `/speckit-plan` decision; the requirement is "not unauthenticated on the open internet".
- **FR-026d**: OpenTelemetry **tracing** SDK initialization is **out of scope for this feature** and lands with the first feature that introduces inter-service work beyond the `/v1/internal/whoami` probe. Trace propagation headers MAY be forwarded if a library does so by default, but no tracer provider, span processor, or exporter is configured by the skeleton. Existing fields like `trace_id` in JSON logs (FR-026a) are populated from request headers when present and otherwise generated locally; they are not produced by an OTel SDK in this feature.
- **FR-026e**: All observability scaffolding MUST honor the same env-var-only configuration discipline as the rest of the skeleton (FR-010, FR-013): switching between `local` and `prod` MUST NOT require source changes to logger or metrics configuration.

### Functional Requirements — Quality (Day-0)

- **FR-027**: `ruff check` and `mypy --strict` MUST be configured for `agent/` with the project's anti-vibe rules (no `Any` in signatures, no `print()`, `Decimal` mandated for currency where it appears) and MUST run via `make lint` and pre-commit.
- **FR-028**: `ktlint` MUST be configured for `finance-api/` with the project's anti-vibe rules (no `Double`/`Float` for currency, no raw stack-trace returns from controllers) and MUST run via `make lint` and pre-commit.
- **FR-029**: `gitleaks` MUST scan staged files on every `git commit` and MUST scan the working tree as part of `make lint`. Detection MUST block the commit / fail the lint run.
- **FR-030**: `make lint` MUST exit non-zero if any tool reports failures but MUST still report all failures across all tools in a single run (no early termination that hides a downstream failure).
- **FR-031**: All linter configuration files (`ruff.toml` or `pyproject.toml` `[tool.ruff]`, `mypy.ini`, `.editorconfig`/`ktlint` config, `.gitleaks.toml`, `.pre-commit-config.yaml`) MUST be committed to the repository. Any future CI MUST consume the same files.

### Functional Requirements — Out of Scope (Negative Requirements)

- **FR-032**: This feature MUST NOT introduce any domain-level controller, service, repository, agent tool, prompt, LLM call, audit-log writer, or rate-limiter beyond what is necessary to make the skeleton probe (FR-020) and `/health` endpoints work. Anything resembling a `TransactionController`, `BalanceProjector`, `Agent.run()`, or `@agent.tool` is explicitly out of scope.
- **FR-033**: This feature MUST NOT freeze any prompt. The `agent/orchestrator/prompts/` directory MAY exist as an empty scaffold, but it MUST NOT contain a `system_v1.md` marked `# FROZEN`.
- **FR-034**: This feature MUST NOT define LLM output schemas in `agent/orchestrator/schemas.py` beyond what is needed for the M2M auth probe. The schema surface is reserved for downstream features under Tier 1 governance (constitution §VII).

### Key Entities *(infrastructure-level)*

- **Workspace Profile** — Logical binding of "where am I running" (`local` vs `prod`) to the concrete endpoints (Postgres URL, Redis URL, M2M signing key source, Spring base URL). One profile per environment; identity is `(service, profile_name)`.
- **Environment Variable Manifest** — The canonical list of variables consumed by either service. Source of truth: `.env.example` for Python, `application-{profile}.yml` for Spring. The manifest is the contract between developer machines and any future cloud secret store.
- **Service Health Report** — Structured response produced by `/health` on either service, containing per-dependency status. Consumed by humans (browser, curl) and downstream by orchestrators (e.g., DigitalOcean App Platform health-check probe).
- **M2M Token** — Short-lived (≤ 15 min) RS256 JWT signed by the Python agent, verified by Spring. Carries claims (`sub`, `aud`, `iss`, `exp`, `user_id`, `intent`, `trace_id`). Not persisted; minted per outbound call.
- **JWKS Endpoint** — Public-key publication surface on Spring (`/.well-known/jwks.json`). Consumed by Spring's own JWT verifier (key-rotation-friendly) and by any future audit tooling.
- **Baseline Schema Migration** — The single Flyway script (`V1__baseline.sql`) that defines what "an empty Luci database" looks like: `pgvector` enabled, Flyway history table present, and a marker artifact. All future migrations land on top of this baseline.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new developer, starting from a clean machine with only Docker, JDK 21, and Python 3.12 installed, reaches a working local stack (both `/health` endpoints green) within **30 minutes** of `git clone`, measured wall-clock.
- **SC-002**: After `make up` completes, both `/health` endpoints return `200 OK` within **60 seconds**, on at least 19 of 20 consecutive runs on a developer-class machine (≥ 8 GB RAM, SSD).
- **SC-003**: The `make lint` command completes in **under 30 seconds** on a warm cache and **under 90 seconds** on a cold cache, for a repository of this skeleton's size, on the same developer-class machine.
- **SC-004**: An engineer with the documented environment variables for Supabase + a remote Redis can run the same `finance-api/` artifact against the remote stack and observe both `/health` endpoints green **without modifying any source file** (only env-var and profile changes are permitted).
- **SC-005**: The M2M JWT auth seam rejects 100% of unsigned, expired, or wrongly-audienced requests (zero false negatives across a 100-request synthetic test mix of valid/invalid tokens) — verified by an integration test that ships with this feature.
- **SC-006**: Introducing a deliberate violation of any documented anti-vibe rule (e.g., a `print()` in `agent/`, a `Double` for currency in `finance-api/`, an AKIA-prefixed string anywhere) MUST be blocked at pre-commit or `make lint` time in **100% of cases** across a 10-violation synthetic suite.
- **SC-007**: The baseline Flyway migration applies cleanly to a fresh Postgres (local or Supabase) on the **first run** and reports zero pending migrations on the **second run** (idempotency verifiable from Flyway's own history table).
- **SC-008**: Stopping any single infrastructure container (`docker stop luci_postgres` or `docker stop luci_redis`) results in the dependent service's `/health` reporting the corresponding dependency as `DOWN` within **one health-check cycle** (≤ 10 seconds) — the failure is never silent.
- **SC-009**: The total count of NEW non-test source files introduced by this feature outside of `agent/`, `finance-api/`, `infra/` (or `docker-compose.yml` at root), `Makefile`, `.env.example`, configuration files, and a single empty `openapi.yaml` is **zero** — i.e., no incidental scaffolding sneaks in.

## Assumptions

- **A-1 — Compose file location**: The user requested `docker-compose.yml` at the repository root, while TDD §17.3 places it under `infra/docker-compose.yml`. This spec assumes the compose definition is hosted at `infra/docker-compose.yml` (TDD-aligned) and that the repository root either contains a thin top-level `docker-compose.yml` that `include:`s the infra file, or that the Makefile uses `docker compose -f infra/docker-compose.yml` so the developer's "from the root" ergonomics are preserved without violating TDD §17.3. The exact placement is a `/speckit-plan` decision; either form satisfies FR-005.
- **A-2 — M2M signing in local dev**: Local dev uses an **RS256** keypair (per constitution; HS256 is not acceptable even locally). Per FR-021, `make bootstrap` **MUST** generate a 2048-bit keypair on first run, persist the private half to `.env.local` (gitignored), and place the public half where Spring's local profile reads it. The target is idempotent: re-runs preserve an existing valid keypair. Cloud `prod` uses keys injected from the secrets manager (1Password Developer Tools per TDD §9.2); this spec assumes the secrets manager is provisioned, but its configuration is downstream work.
- **A-3 — Cloud target**: The TDD names DigitalOcean App Platform as the target host (§4.1, §4.2). This spec does NOT depend on any DO-specific feature; it only requires that the env-var surface defined here can be populated by the cloud target's secret-injection mechanism. Substituting Cloud Run / Heroku / Fly.io requires zero changes to this spec.
- **A-4 — Remote Redis vendor**: Upstash is the TDD-named candidate (§4.2 / §12.1). This spec assumes any remote Redis 7-compatible service with TLS-required ingress will work; no vendor-specific extension is required by the skeleton.
- **A-5 — Supabase project provisioning**: The Supabase project itself (account creation, IP allow-list configuration, service-role key generation) is operator work performed before Story 3 can be exercised. This spec does not automate Supabase project creation.
- **A-6 — TLS termination**: For local dev, both services bind to plaintext HTTP on localhost (`:8000`, `:8080`). For cloud, TLS is terminated by the App Platform edge (per TDD §8.2). This spec assumes neither service implements its own TLS termination.
- **A-7 — Skeleton probe endpoint**: A `POST /v1/internal/whoami` endpoint is introduced solely to validate the M2M auth seam (Story 2). It does not appear in the product PRD because it is internal scaffolding; it MAY be retired once a real M2M-authenticated controller exists.
- **A-8 — Clock-skew leeway**: JWT validation tolerates a 60-second leeway. This is the conservative default for distributed systems and is below the 15-minute TTL by two orders of magnitude.
- **A-9 — Eval-suite scaffolding**: The eval gate (constitution §IV) cannot run meaningfully until LLM-touching code exists. This feature scaffolds the directory layout (e.g., empty `agent/tests/fixtures/llm/`) but does NOT introduce eval datasets or thresholds — those land with the first prompt.
- **A-10 — `pgvector` image variant**: The TDD-named image `ghcr.io/tembo-io/pg-pgmq:pg15` carries both `pgmq` and `pgvector`. The skeleton only requires `pgvector`; `pgmq` is exercised by a later async-pipeline feature. The image choice satisfies both.

## Dependencies

- Docker Engine ≥ 24 (developer machine).
- JDK 21 (Temurin or equivalent) and the Gradle wrapper (committed).
- Python 3.12 and `uv` (installed by `make bootstrap`).
- GitHub Spec Kit workflow + the constitution at `.specify/memory/constitution.md` (already present).
- Supabase project (provisioned operator-side) for Story 3 only — NOT blocking for Stories 1, 2, or 4.
- A remote Redis instance for Story 3 only — NOT blocking for Stories 1, 2, or 4.

## Out of Scope (this feature)

- Any domain logic from PRD F1–F10 (intent parsing, transaction ingest, balance/projection/simulation, billing, charts, audit log).
- **LGPD lifecycle invariants**: one-tap `/cancel`, 15-day hard-purge with `CHECK` constraint, and pseudonymized audit-log retention (5-year `sha256(user_id || pepper)`) are explicitly deferred to a downstream feature. Silence in this spec MUST NOT be read as "already satisfied" — these invariants require dedicated schema, controller, and worker implementations that do not exist in the skeleton.
- OpenAPI documentation of **domain** endpoints (transactions, balance, billing, etc.). The skeleton's `openapi.yaml` documents only the three skeleton-level endpoints (`whoami`, `/health`, JWKS) per FR-002.
- The eval-suite datasets and thresholds (the `luci-evals` submodule reference may be wired, but no dataset is committed).
- CI pipeline definitions (`.github/workflows/`) — this spec defines what `make lint` does locally; the same hooks running in CI is a follow-on feature.
- **Routing telemetry to real backends** (a Prometheus server scraping `/metrics`, an OTel collector receiving traces, Langfuse). The skeleton lands the *production* surfaces required by constitution §V — structured JSON logs and a Prometheus scrape endpoint on both services (FR-026a, FR-026b) — but does NOT stand up the collectors that consume them. **OpenTelemetry tracing SDK initialization** is also out of scope per FR-026d and lands with the first feature that has inter-service work beyond the `whoami` probe.
- Mercado Pago integration, Telegram bot integration, Vertex AI integration. Stubs (MockServer, dev bot tokens) MAY appear in the compose stack; live integrations do not.
- The `make seed`, `make seed-pdf`, `make seed-fail` data-loading targets — `make reset` is in scope, but the seed scripts themselves are a downstream feature once there is a schema to seed.
