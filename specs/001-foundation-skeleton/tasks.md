---
description: "Task list for Foundation — Walking Skeleton (Local + Cloud)"
---

# Tasks: Foundation — Walking Skeleton (Local + Cloud)

**Input**: Design documents from `/specs/001-foundation-skeleton/`

**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/openapi.yaml ✓, quickstart.md ✓

**Tests**: Included — the spec explicitly ships named test files (`test_skeleton.py`, `WhoamiIntegrationTest.kt`, `HealthEndpointIntegrationTest.kt`, `FlywayBaselineTest.kt`) and SC-005 mandates "an integration test that ships with this feature."

**Organization**: Tasks are grouped by user story (US1–US4) to enable independent implementation and testing of each story. The four user stories from spec.md are: US1 Day-1 local bootstrap (P1), US2 Authenticated M2M channel (P2), US3 Cloud parity via profile+env swap (P3), US4 Day-0 quality gates (P4). Within each user-story phase, **Tests appear before Implementation** to enforce TDD discipline (constitution §IV; speckit.superb.tdd mandatory hook).

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to ([US1]–[US4])
- File paths are relative to repo root unless stated otherwise

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the monorepo directory skeleton, initialize both service build systems, and place the T1 contract seam before any code is written.

- [x] T001 Create monorepo directory structure: `agent/`, `finance-api/`, `infra/`, `scripts/`, `docs/` (already exists), `specs/` (already exists), `openapi.yaml` placeholder at repo root per plan.md §Project Structure
- [x] T002 [P] Initialize Python service in `agent/pyproject.toml` with all runtime deps (`fastapi`, `uvicorn[standard]`, `pydantic`, `pydantic-settings`, `httpx`, `pyjwt[crypto]`, `cryptography`, `psycopg[binary,pool]`, `redis>=5`, `structlog`, `prometheus-client`) and dev deps (`ruff`, `mypy`, `pytest`, `pytest-asyncio`, `respx`, `openapi-python-client>=0.21` — version pin ensures full OpenAPI 3.1.0 support per F2); create `agent/.python-version` pinned to `3.12` (R-15)
- [x] T003 [P] Initialize Kotlin/Spring service in `finance-api/build.gradle.kts` (Kotlin DSL) with all deps: starters (`web`, `actuator`, `data-redis`, `jdbc`, `security`, `oauth2-resource-server`), observability (`micrometer-registry-prometheus`, `logstash-logback-encoder`), docs (`springdoc-openapi-starter-webmvc-ui`), JWT (`nimbus-jose-jwt`), DB (`postgresql`, `flyway-core`, `flyway-database-postgresql`), test (`spring-boot-starter-test`, `testcontainers:postgresql`, `testcontainers:junit-jupiter`, `com.redis:testcontainers-redis`), **and `developmentOnly("org.springframework.boot:spring-boot-devtools")` so `./gradlew bootRun` triggers automatic restart on classpath changes (Story 1 AS-5 — live-reload)**, and `org.jlleitschuh.gradle.ktlint` plugin (R-16); create `finance-api/settings.gradle.kts`
- [x] T004 [P] Commit Gradle wrapper to `finance-api/`: run `./gradlew wrapper --gradle-version 8.10.2 --distribution-type bin` (pinned to current stable GA per A1) and commit `finance-api/gradlew`, `finance-api/gradlew.bat`, `finance-api/gradle/wrapper/gradle-wrapper.jar`, `finance-api/gradle/wrapper/gradle-wrapper.properties`
- [x] T005 Copy `specs/001-foundation-skeleton/contracts/openapi.yaml` to repo root `openapi.yaml` (T1 seam, FR-002, R-6); this file is the source of truth for the generated Python client and the springdoc parity check

**Checkpoint**: Build system initialized — `uv pip install` and `./gradlew dependencies` can both resolve from their respective roots.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure that MUST be complete before any user story can be implemented — env manifest, docker-compose, key bootstrap, Makefile, Spring app entry, Flyway migration, logging scaffolding.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T006 Create `.gitignore` at repo root excluding: `.env.local`, `agent/.venv`, `finance-api/build/`, `finance-api/.gradle/`, `*.class`, `*.jar`, `__pycache__/`, `.mypy_cache/`, `.ruff_cache/`. **DO NOT** add `agent/finance_api_client/` to `.gitignore` — per R-6 and FR-002b the generated client is committed to the repo so drift is diff-visible in code review (resolves G2 contradiction).
- [x] T007 Create `.env.example` at repo root with every env var from data-model.md §2 (R-18, FR-011), grouped: Profile+service IDs (`LUCI_ENV`, `SPRING_PROFILES_ACTIVE`), Database (`LUCI_DB_URL`, `LUCI_DB_USER`, `LUCI_DB_PASSWORD`, `LUCI_DB_DSN`), Cache (`LUCI_REDIS_URL`), M2M JWT (`LUCI_M2M_ISSUER`, `LUCI_M2M_AUDIENCE`, `LUCI_M2M_KID`, `LUCI_M2M_PRIVATE_KEY_PEM`, `LUCI_M2M_PUBLIC_KEY_PEM`, `LUCI_M2M_JWKS_URL`), Inter-service (`SPRING_BASE_URL`), Observability (`LUCI_LOG_LEVEL`)
- [x] T008 Create `infra/docker-compose.yml` with `luci_postgres` (`ghcr.io/tembo-io/pg-pgmq:pg15`, port 5432, healthcheck `pg_isready`, named volume `luci_pgdata`) and `luci_redis` (`redis:7-alpine`, port 6379, append-only persistence enabled, healthcheck `redis-cli ping`); compose project pinned to `luci` (R-10, R-11, FR-005, FR-006, FR-007)
- [x] T009 Create `scripts/bootstrap-keys.sh`: idempotent 2048-bit RS256 keypair generator — checks `LUCI_M2M_PRIVATE_KEY_PEM` in `.env.local`; if absent or unparseable, uses `openssl genpkey` to generate keypair, writes PEM-encoded private and public halves + `LUCI_M2M_KID=luci-m2m-<unix-epoch>` into `.env.local`; exits 0 on re-run if valid key already present (R-5, FR-021)
- [x] T010 Create `Makefile` at repo root with targets (command forms MUST match `quickstart.md` §6 verbatim, per F1):
  - `bootstrap` — installs uv, verifies JDK 21, runs `pre-commit install`, copies `.env.example` → `.env.local` if absent, calls `scripts/bootstrap-keys.sh`, calls `make codegen`
  - `up` — `docker compose -f infra/docker-compose.yml --project-name luci up -d --wait`
  - `dev` — backgrounds `uvicorn agent.main:app --reload --host 0.0.0.0 --port 8000` + `./gradlew :finance-api:bootRun` (Spring restart-on-change enabled via `spring-boot-devtools` from T003), traps SIGINT
  - `lint` — runs the following sequentially, capturing exit codes; reports all output but exits non-zero if any single tool failed (FR-030): `ruff check agent/`, `mypy --strict agent/`, `./gradlew :finance-api:ktlintCheck`, `gitleaks detect --source .`, `scripts/lint-no-spring-httpx.sh` (from T049a, Gap-4)
  - `codegen` — `openapi-python-client generate --path openapi.yaml --output-path agent/finance_api_client --overwrite`
  - `reset` — `docker compose -f infra/docker-compose.yml --project-name luci down -v && make up`
  - `verify-m2m` — `uv run pytest agent/tests/test_skeleton.py::test_whoami_round_trip -v`
  - **Port-conflict handling (B1)**: rely on Docker Compose's native `Bind for 0.0.0.0:<port> failed: port is already allocated` for 5432/6379, and on the OS's `[Errno 98] Address already in use` for uvicorn (8000) / `Web server failed to start. Port 8080 was already in use.` for Spring (8080). All four are human-readable; no Makefile wrapper script added. Document this in a leading comment of the `up` and `dev` targets so failures are not misread as Makefile bugs.
  - (FR-004, R-10, R-13, FR-008, spec Edge Cases)
- [x] T011 [P] Create `finance-api/src/main/kotlin/app/luci/finance/FinanceApiApplication.kt` Spring Boot entry point with `@SpringBootApplication`
- [x] T012 [P] Create `finance-api/src/main/resources/db/migration/V1__baseline.sql` with `CREATE EXTENSION IF NOT EXISTS vector;` + `COMMENT ON TABLE flyway_schema_history IS 'Luci baseline migration applied. Walking skeleton — no domain tables yet.';` (FR-014, R-12)
- [x] T013 Create `finance-api/src/main/resources/application.yml` with: Actuator base-path `/`, path-mapping `health=health` and `prometheus=metrics`, health `show-components=always` and `show-details=always`, **springdoc properties `springdoc.api-docs.path=/openapi.yaml` and `springdoc.swagger-ui.enabled=false` (path config lives HERE; T018 only wires the `GroupedOpenApi` bean — resolves F3 double-configuration)**, server port 8080, Flyway location `classpath:db/migration`, datasource from `LUCI_DB_URL`/`LUCI_DB_USER`/`LUCI_DB_PASSWORD`, redis URL from `LUCI_REDIS_URL`, JWT issuer from `LUCI_M2M_ISSUER` (R-7, R-8, FR-024)
- [x] T014 [P] Create `agent/observability/logging.py` with `structlog` JSON config: `JSONRenderer`, `merge_contextvars`, stdlib `logging` forwarded through structlog, log-level from `LUCI_LOG_LEVEL` env var, static `service: agent` field (FR-026a, R-9, FR-026e)
- [x] T015 [P] Create `finance-api/src/main/resources/logback-spring.xml` using `logstash-logback-encoder`'s `LogstashEncoder` writing single-line JSON with `@timestamp`, `level`, `logger`, `message`, `traceId` (from MDC), static `service: finance-api` field; log-level from `LUCI_LOG_LEVEL` env var (FR-026a, R-9, FR-026e)
- [x] T016 [P] Create `finance-api/src/main/kotlin/app/luci/finance/observability/LoggingConfig.kt` optional MDC-propagation bean for request-scope context (`traceId`, `service`, `profile`) (R-9)

**Checkpoint**: Foundation ready — both service build systems resolve, docker-compose can start infra, and the Makefile entry points exist. User story implementation can begin.

---

## Phase 3: User Story 1 — Day-1 Local Bootstrap (Priority: P1) 🎯 MVP

**Goal**: New engineer clones repo, runs three commands, and within 30 minutes has both services healthy against local Postgres+pgvector and Redis. Both `/health` endpoints return `200 OK` with `db: UP` and `redis: UP` (SC-001).

**Independent Test**: On a clean machine with Docker, JDK 21, Python 3.12 installed — `git clone && make bootstrap && make up && make dev` — both `curl localhost:8080/health` and `curl localhost:8000/health` return `200 OK` with `db: UP`, `redis: UP` within 30 min wall-clock.

### Tests for User Story 1

> **Write these tests FIRST and ensure they FAIL before implementing T017–T026 (TDD; constitution §IV).**

- [x] T017 [US1] Write `agent/tests/test_skeleton.py` health probe tests: `test_health_up` (with Postgres + Redis running, assert 200 + `status=UP` + components), `test_health_db_down` (no DB env, assert 503 + `components.db.status=DOWN`), `test_health_redis_down` (no Redis env, assert 503 + `components.redis.status=DOWN`) — these run against a live local stack (respx NOT used for infra calls, per plan.md testing strategy)
- [x] T018 [US1] Write `finance-api/src/test/kotlin/app/luci/finance/HealthEndpointIntegrationTest.kt` with Testcontainers (`@Testcontainers`, Postgres + pgvector + Redis): assert `GET /health` returns 200 + Actuator shape with `db=UP` and `redis=UP`; stop Redis container, assert `GET /health` returns 503 with `redis=DOWN` within 10 s (FR-025, SC-008)
- [x] T019 [US1] Write `finance-api/src/test/kotlin/app/luci/finance/FlywayBaselineTest.kt` with Testcontainers Postgres: apply Flyway migration, query `pg_description` for the marker comment (`'Luci baseline migration applied. Walking skeleton — no domain tables yet.'`), assert present; restart against same DB, assert Flyway reports 0 pending migrations (FR-014, FR-016, SC-007)

### Implementation for User Story 1

- [x] T020 [P] [US1] Create `finance-api/src/main/resources/application-local.yml` with: `spring.datasource.url=jdbc:postgresql://localhost:5432/luci`, `spring.data.redis.url=redis://localhost:6379/0`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/.well-known/jwks.json`, `management.endpoint.health.cache.time-to-live=0s` (R-17, FR-009, FR-025)
- [x] T021 [P] [US1] Create `finance-api/src/main/kotlin/app/luci/finance/config/OpenApiConfig.kt` with a `GroupedOpenApi` bean (group name `luci-skeleton`, paths-to-match `/v1/**`, `/health`, `/.well-known/**`). **DO NOT** set the springdoc path here — that lives in `application.yml` per T013 (resolves F3) (FR-002a)
- [x] T022 [US1] Create `finance-api/src/main/kotlin/app/luci/finance/config/SecurityConfig.kt` Spring Security config: permit unauthenticated `GET /health`, `GET /metrics`, `GET /.well-known/**`; require M2M JWT for `POST /v1/internal/**` (configured to accept any bearer token for now — JWT resource-server wiring added in T034). **Forward-compatibility note (C3)**: `POST /v1/users/**` is reserved by FR-018 for future domain endpoints; the matcher MUST also gate `/v1/users/**` so the first domain feature does not need to remember to add it. Deny all others. (R-3, FR-018, FR-023)
- [x] T023 [P] [US1] Create `agent/config/settings.py` with pydantic-settings `Settings` model reading all env vars from data-model.md §2; fail-fast at import if any required var is absent (FR-010)
- [x] T024 [P] [US1] Create `agent/observability/metrics.py` with `prometheus_client` ASGI app and `Counter("http_requests_total", ["method", "route", "status"])` middleware (FR-026b, R-8)
- [x] T025 [P] [US1] Create `agent/health/routes.py` FastAPI router `GET /health`: **probes Postgres (`SELECT 1` via `psycopg`) and Redis (`PING` via redis-py) in parallel using `asyncio.gather(...)`, with each probe wrapped in `asyncio.wait_for(..., timeout=1.0)` so total worst-case latency is 1 s, not 2 s (resolves C1 underspec)**; returns Actuator-shaped JSON `{"status": "UP|DOWN", "components": {"db": {"status": ...}, "redis": {"status": ...}}}`; HTTP 200 on UP, 503 on DOWN (FR-023, FR-024, FR-025, FR-026, R-7)
- [x] T026 [US1] Create `agent/main.py` FastAPI app factory: configure structlog (calls `logging.py`), mount `GET /health` router, mount `GET /metrics` as ASGI sub-app, include orchestrator router stub (FR-003)
- [x] T027 [P] [US1] Create `agent/orchestrator/__init__.py` empty module and `agent/orchestrator/prompts/` empty directory scaffold (FR-033)
- [x] T028 [P] [US1] Create `agent/tests/fixtures/llm/` empty directory scaffold (spec A-9)
- [x] T029 [US1] Create `agent/tests/conftest.py` with `pytest-asyncio` mode=`auto` and an `AsyncClient` fixture wrapping the FastAPI app

**Checkpoint**: US1 fully functional — `make bootstrap && make up && make dev` produces two green `/health` endpoints; Flyway migration verified idempotent; health DOWN propagation verified within 10 s.

---

## Phase 4: User Story 2 — Authenticated Service-to-Service Channel (Priority: P2)

**Goal**: Python agent issues a signed M2M RS256 JWT call to Spring's `/v1/internal/whoami` and receives the verified claims echoed back. Unsigned, expired, and wrongly-audienced calls are rejected with `401` before any controller body runs (SC-005, FR-018).

**Independent Test**: `make verify-m2m` runs `agent/tests/test_skeleton.py::test_whoami_round_trip` against local services. Valid RS256 JWT → 200 OK with echoed claims. No `Authorization` header → 401. JWT signed by throwaway key → 401.

### Tests for User Story 2

> **Write these tests FIRST and ensure they FAIL before implementing T032–T041 (TDD; constitution §IV).**

- [x] T030 [US2] Write `finance-api/src/test/kotlin/app/luci/finance/WhoamiIntegrationTest.kt` with Testcontainers (Postgres + Redis): named test methods covering each equivalence class — `valid_jwt_echoes_claims` (asserts 200 + claims), `missing_authorization_returns_401`, `expired_jwt_returns_401`, `wrong_key_signature_returns_401`, `wrong_audience_returns_401`, `wrong_issuer_returns_401`, **`iat_far_in_future_returns_401_with_distinct_marker` (Gap-3 — verifies that a JWT minted with `iat = now + 16 min` produces log marker `m2m.jwt.iat_future`, distinct from `m2m.jwt.expired`; spec Edge Case "Clock skew exceeds JWT TTL")**; assert no stack traces in any 401 body; assert the log marker for each 401 case matches the failure class (FR-018, FR-022, SC-005)
- [x] T031 [US2] Write `finance-api/src/test/kotlin/app/luci/finance/WhoamiAuthFuzzTest.kt` — **parametrized fuzz test (Gap-6, SC-005 "100-request synthetic mix")** using `@ParameterizedTest` with a seeded `Random` (`seed = 20260515L`) generating 100 cases distributed across 7 equivalence classes (≈14 valid + ≈86 invalid spread across 6 invalid sub-classes); asserts (a) zero valid-tokens receive 401, (b) zero invalid-tokens receive 200; total runtime ≤ 30 s under Testcontainers
- [x] T032 [US2] Write `agent/tests/test_skeleton.py::test_whoami_round_trip` integration test: uses `mint_m2m_token()` + generated `AuthenticatedClient` to call `POST /v1/internal/whoami` against local Spring; assert 200 + `subject="service:python-agent"`, `user_id`, `intent`, `trace_id` echoed; repeat with raw `httpx` and no `Authorization` → assert 401; repeat with throwaway-key JWT → assert 401 (quickstart §4, SC-005, FR-002b — uses generated client, not hand-written httpx)

### Implementation for User Story 2

- [x] T033 [US2] Create `agent/http/m2m_auth.py` RS256 JWT minting with PyJWT: `mint_m2m_token(user_id, intent, trace_id)` → signed JWT with claims `iss=LUCI_M2M_ISSUER`, `aud=LUCI_M2M_AUDIENCE`, `sub=service:python-agent`, `exp=iat+15min`, `user_id`, `intent`, `trace_id`; `alg` pinned to `RS256`; `kid` from `LUCI_M2M_KID`; private key loaded from `LUCI_M2M_PRIVATE_KEY_PEM` (FR-017, R-4)
- [x] T034 [US2] Generate `agent/finance_api_client/` by running `openapi-python-client generate --path openapi.yaml --output-path agent/finance_api_client --overwrite` (via `make codegen`); **commit the generated output to the repo per R-6 — this directory is NOT in `.gitignore` (see T006)**; verify `from agent.finance_api_client import AuthenticatedClient` imports cleanly (FR-002b, R-6)
- [x] T035 [P] [US2] Create `agent/http/spring_client.py` thin wrapper: `get_spring_client()` returning an `AuthenticatedClient` from the generated library configured with `SPRING_BASE_URL` and the M2M bearer token from `mint_m2m_token` (FR-002b)
- [x] T036 [US2] Create `finance-api/src/main/kotlin/app/luci/finance/config/M2MKeyConfig.kt` `@Configuration` bean: reads `LUCI_M2M_PUBLIC_KEY_PEM` from env at startup, parses into RSA `PublicKey`, constructs an in-memory `JWKSet` with `kid=LUCI_M2M_KID`; exposes `JWKSet` as a Spring bean. **Idempotency contract**: if `LUCI_M2M_PUBLIC_KEY_PEM` is absent or unparseable, throw `IllegalStateException` from the bean factory so Spring's `ApplicationContext` fails at boot with a single named-variable error (Gap-2; spec Edge Case "JWT signing key not configured in prod"). (R-3, FR-019, FR-021)
- [x] T037 [US2] Create `finance-api/src/main/kotlin/app/luci/finance/api/JwksController.kt` `GET /.well-known/jwks.json`: returns the `JWKSet` bean serialised to RFC 7517 JSON, Content-Type `application/json`, unauthenticated (FR-019)
- [x] T038 [US2] Create `finance-api/src/main/kotlin/app/luci/finance/api/WhoamiController.kt` `POST /v1/internal/whoami`: extracts verified `sub`, `user_id`, `intent`, `trace_id` from the `JwtAuthenticationToken` principal; returns `WhoamiResponse` JSON; no business logic (FR-020, contracts/openapi.yaml `WhoamiResponse` schema)
- [x] T039 [P] [US2] Create `finance-api/src/main/kotlin/app/luci/finance/api/advice/GlobalExceptionHandler.kt` `@RestControllerAdvice`: catches any `Exception` and returns a `ProblemDetail`-shaped 500 body; **Spring Security's 401 handling is delegated to a custom `AuthenticationEntryPoint` (see T041) — not overridden here** (FR-018, constitution anti-vibe: no raw stack traces)
- [x] T040 [US2] Update `finance-api/src/main/kotlin/app/luci/finance/config/SecurityConfig.kt` to wire the JWT resource server: `jwk-set-uri` pointed at `LUCI_M2M_JWKS_URL` (self-loopback in local), `JwtAuthenticationConverter` validating `aud=LUCI_M2M_AUDIENCE`, `NimbusJwtDecoder` with `setJwtValidator(...)` chaining `JwtTimestampValidator(Duration.ofSeconds(60))` (60 s clock-skew leeway) + an audience validator (FR-018, FR-022, R-3). **Forward-compatibility (C3)**: the resource-server filter MUST also match `/v1/users/**` (already declared in T022's matcher list) so future domain endpoints inherit JWT verification without re-wiring.
- [x] T041 [US2] Create JWT failure-marker pipeline (**concrete mechanism, replaces T038 placeholder; Gap-3**):
  - **`finance-api/src/main/kotlin/app/luci/finance/security/M2MAuthenticationEntryPoint.kt`** — implements Spring's `AuthenticationEntryPoint`; writes a minimal `ProblemDetail` 401 body (no stack trace, no JWT internals).
  - **`finance-api/src/main/kotlin/app/luci/finance/security/JwtFailureEventListener.kt`** — `@Component` implementing `ApplicationListener<AbstractAuthenticationFailureEvent>`; inspects the failure exception's class + `OAuth2Error.description` and logs at WARN with a structured `event` field matching exactly one of the **5 markers** documented in data-model.md §4:
    - `m2m.jwt.missing` (no `Authorization` header)
    - `m2m.jwt.invalid_signature` (unknown `kid` or wrong key)
    - `m2m.jwt.expired` (`exp + leeway < now`)
    - `m2m.jwt.audience_mismatch` (`aud` does not match)
    - **`m2m.jwt.iat_future`** (NEW — `iat > now + leeway` or `nbf > now + leeway`; spec Edge Case "Clock skew > JWT TTL")
  - Wire the `AuthenticationEntryPoint` into `SecurityConfig.kt` (T040). Update `data-model.md` §4 to document the 5th marker (out-of-band change, tracked in T055).
  - (FR-018, FR-022, Gap-3, T038 placeholder resolution)

**Checkpoint**: US2 functional — `make verify-m2m` passes; Spring rejects 100% of invalid tokens across the **five** failure classes; the parametrized fuzz test (T031) reports 0 false positives + 0 false negatives across 100 cases (SC-005).

---

## Phase 5: User Story 3 — Cloud Parity via Profile + Env Swap (Priority: P3)

**Goal**: Same committed artifacts run against Supabase Postgres + remote Redis by changing only env vars and `SPRING_PROFILES_ACTIVE=prod`. Both `/health` endpoints report green. Flyway migration is idempotent (SC-004, FR-009, FR-013).

**Independent Test**: Engineer sets documented `LUCI_DB_*`, `LUCI_REDIS_*`, `SPRING_PROFILES_ACTIVE=prod` vars pointing at a Supabase project + Upstash Redis, runs `make dev` locally (no source change), both `/health` return `200 OK`, Flyway applies once and is idempotent on restart.

### Tests for User Story 3

> **Write these tests FIRST and ensure they FAIL before implementing T046–T049 (TDD; constitution §IV; resolves E1/E2/E3).**

- [x] T042 [US3] Write `agent/tests/test_settings.py` (E1 — TDD test for T046): assert `Settings(LUCI_ENV='prod', LUCI_DB_DSN='postgresql://...@host/db')` (plain) raises `ValueError` naming `LUCI_DB_DSN`; assert `Settings(LUCI_ENV='prod', LUCI_DB_DSN='postgresql://...?sslmode=require', LUCI_REDIS_URL='redis://...')` raises `ValueError` naming `LUCI_REDIS_URL`; assert the same fields are accepted when prefixed with `rediss://` and `sslmode=require`; assert local profile (`LUCI_ENV='local'`) accepts both forms (FR-010, FR-012)
- [x] T043 [US3] Write `finance-api/src/test/kotlin/app/luci/finance/ProdProfileSecurityTest.kt` (E2 — TDD test for T047): boots Spring with `SPRING_PROFILES_ACTIVE=prod` and a stubbed `request.remoteAddr` outside the allowed CIDR; asserts `GET /.well-known/jwks.json`, `POST /v1/internal/whoami`, `GET /metrics` all return `403 Forbidden`; with `request.remoteAddr` inside the allowed CIDR (e.g., `127.0.0.1` for the test), asserts the same paths are reachable (FR-019, FR-020, FR-026c)
- [x] T044 [US3] Write `finance-api/src/test/kotlin/app/luci/finance/FailFastIntegrationTest.kt` (E3 + Gap-2 — TDD test for T048, replaces original T044 placeholder): with Testcontainers, attempt to boot Spring with `SPRING_PROFILES_ACTIVE=prod` and `LUCI_DB_URL` unset → assert `ApplicationContextException` whose root-cause message contains the literal string `LUCI_DB_URL`; repeat with `LUCI_M2M_PUBLIC_KEY_PEM` unset → assert the boot failure message contains `LUCI_M2M_PUBLIC_KEY_PEM` (proves prod refuses dev-key fallback; spec Edge Case "JWT signing key not configured in prod") (story 3 AS-4, Gap-2)
- [x] T045 [US3] Write `agent/tests/test_skeleton.py::test_settings_prod_fail_fast` — assert `Settings()` raises `ValueError` naming the first missing required var when `LUCI_ENV=prod` and `LUCI_M2M_PRIVATE_KEY_PEM` is unset (mirrors Spring-side test; FR-010)

### Implementation for User Story 3

- [x] T046 [P] [US3] Strengthen `agent/config/settings.py` (was T042): add `model_validator(mode="after")` checking that in `prod` (`LUCI_ENV=prod`) `LUCI_DB_DSN` includes `sslmode=require` and `LUCI_REDIS_URL` starts with `rediss://`; on any violation raise `ValueError` naming the offending variable (FR-010, FR-012, data-model.md §1 validation rules, story 3 AS-4)
- [x] T047 [P] [US3] Add `@Profile("prod")` `SecurityConfig` bean to `finance-api/src/main/kotlin/app/luci/finance/config/SecurityConfig.kt` (**concrete mechanism, replaces T043 placeholder**): a separate `@Bean @Profile("prod") fun prodSecurityFilterChain(http: HttpSecurity): SecurityFilterChain` returning a chain that requires `hasIpAddress("10.0.0.0/8").or(hasIpAddress("127.0.0.1/32"))` for `GET /.well-known/**`, `POST /v1/internal/**`, and `GET /metrics`; the local profile retains the unrestricted bean from T022 (FR-019, FR-020, FR-026c, R-17). The actual production CIDR is sourced from `${LUCI_INTERNAL_CIDR:127.0.0.1/32}` so deployments inject their platform's range without code edits (FR-013).
- [x] T048 [US3] Create `finance-api/src/main/resources/application-prod.yml` with env-var placeholders for all prod-only settings: `spring.datasource.url=${LUCI_DB_URL}`, `spring.datasource.username=${LUCI_DB_USER}`, `spring.datasource.password=${LUCI_DB_PASSWORD}`, `spring.data.redis.url=${LUCI_REDIS_URL}`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${LUCI_M2M_JWKS_URL}`, `luci.internal-cidr=${LUCI_INTERNAL_CIDR}`, TLS-required connection properties for JDBC (Spring picks `?sslmode=require` up from the URL) and Redis (Lettuce auto-detects `rediss://`); no hardcoded values; absence of any `${LUCI_*}` placeholder MUST cause `ApplicationContextException` at boot (verified by T044) (R-17, FR-009, FR-012)
- [x] T049 [US3] **DELETED** — the original T044 ("Verify fail-fast at boot") is replaced by the concrete test T044 above + implementation T048's no-hardcoded-defaults discipline. Spring Boot's default behavior (unresolved `${VAR}` throws `IllegalArgumentException` at property-binding time) provides the fail-fast guarantee; no `FailureAnalyzer` needed.

**Checkpoint**: US3 functional — switching `SPRING_PROFILES_ACTIVE` between `local` and `prod` (with appropriate env vars) requires zero source changes; both services fail-fast on missing required config; the prod profile cannot serve `/v1/internal/**`, `/.well-known/**`, or `/metrics` to non-internal IPs.

---

## Phase 6: User Story 4 — Day-0 Quality Gates (Priority: P4)

**Goal**: `make lint` passes deterministically on a clean tree (ruff + mypy + ktlint + gitleaks + the "no-httpx-to-Spring" guard, ≤ 30 s warm, ≤ 90 s cold). Pre-commit fires the same hooks on `git commit`. Deliberate violations are blocked 100% (SC-006).

**Independent Test**: Introduce 10 deliberate violations spanning every enforced-at-Day-0 anti-vibe rule (enumerated in T056). `make lint` exits non-zero with each violation attributed to the correct tool. Remove all 10 → `make lint` exits 0.

> **Anti-vibe deferral note (Gap-5, plan.md R-16)**: the constitution's `no Double/Float for currency` rule cannot be enforced by ktlint (it's a style linter, not a semantic one). Per plan.md R-16, Spotbugs/PMD enforcement of currency-type rules **lands with the first feature that introduces currency-handling code**. The skeleton has no currency code (FR-032 ban), so this rule has no testable surface today. T056's 10-violation suite intentionally omits Double-for-currency and substitutes other Day-0-enforceable violations.

### Tests for User Story 4

> **Verification tasks for US4 are operational rather than test-first because the lint configs ARE the tests (each config file is a declarative test of the codebase's compliance). T056 is the integration verification.**

### Implementation for User Story 4

- [x] T050 [P] [US4] Create `ruff.toml` (or `[tool.ruff]` section in `agent/pyproject.toml`) with: `select = ["E","F","I","T20","W"]` (`T20` bans `print()`, `W` catches tab + trailing-whitespace), `ignore = []`, `target-version = "py312"`, `line-length = 100`; add `[tool.ruff.lint.per-file-ignores]` allowlist for generated `finance_api_client/` (FR-027, R-15)
- [x] T051 [P] [US4] Create `mypy.ini` at repo root with `[mypy]` section: `strict = True`, `disallow_any_explicit = True`, `files = agent/`; add `[mypy-agent.finance_api_client.*]` block with `ignore_errors = True` (generated client passes type-checking by ignore, not by strict pass) (FR-027, R-15)
- [x] T052 [P] [US4] Configure ktlint in `finance-api/build.gradle.kts`: add `id("org.jlleitschuh.gradle.ktlint") version "12.1.1"`, configure `reporters { reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN) }`; add `ktlintCheck` to the Gradle `check` task (FR-028, R-14, R-16)
- [x] T053 [P] [US4] Create `.gitleaks.toml` with default Gitleaks rules plus an allowlist entry for the `AKIAIOSFODNN7EXAMPLE` example string in `.env.example` (if present) (FR-029, FR-031)
- [x] T054 [P] [US4] Create `scripts/lint-no-spring-httpx.sh` **(Gap-4 — implements FR-002b's "no hand-written httpx to Spring" constitutional rule, promised in R-6 but previously unimplemented)**: a bash script that `grep -rE`s for `httpx\.(AsyncClient|Client|get|post|put|delete|patch|request)` in `agent/` (excluding `agent/finance_api_client/`) and matches it against any line that ALSO contains `SPRING_BASE_URL`, `finance-api.luci.app`, or `localhost:8080`; exits non-zero with a `file:line:` diagnostic on any match. Make executable (`chmod +x`). Document the allowlist comment convention `# luci-allow-spring-httpx: <reason>` for principled exceptions (FR-002b, R-6)
- [x] T055 [P] [US4] Create `.pre-commit-config.yaml` with hooks (R-14, FR-029, FR-031):
  - `astral-sh/ruff-pre-commit` — `ruff check` + `ruff format --check`
  - local hook `mypy --strict agent/` via `uv run`
  - local hook `./gradlew :finance-api:ktlintCheck`
  - `gitleaks/gitleaks` with `--staged`
  - **local hook `scripts/lint-no-spring-httpx.sh` (Gap-4)**
  - All hook command-forms MUST match what `make lint` runs (T010), so divergence is impossible (FR-031, F1)
- [x] T056 [P] [US4] Create `.editorconfig` at repo root with `charset = utf-8`, `end_of_line = lf`, `insert_final_newline = true`, `trim_trailing_whitespace = true`; Python: `indent_style = space, indent_size = 4`; Kotlin: `indent_style = space, indent_size = 4` (FR-031)

### Validation for User Story 4

- [x] T057 [US4] **Expanded violation suite (Gap-7, SC-006 "10-violation synthetic suite")** — verify `make lint` end-to-end:
  1. Run on clean tree → exits 0 in ≤ 30 s warm (SC-003).
  2. Introduce 10 deliberate violations:
     1. `print("violation-1")` in `agent/main.py` → blocked by **ruff T201**
     2. `def violation(x: Any) -> Any:` with `from typing import Any` in `agent/foo.py` → blocked by **mypy strict (`disallow_any_explicit`)**
     3. Unsorted imports in `agent/bar.py` (`import sys` then `import os` before stdlib alphabetical) → blocked by **ruff I**
     4. Tab character in `agent/baz.py` → blocked by **ruff W191** + `.editorconfig`
     5. Trailing whitespace on a line in `agent/qux.py` → blocked by **ruff W291**
     6. Trailing whitespace in `finance-api/src/main/kotlin/.../Foo.kt` → blocked by **ktlint**
     7. Unsorted Kotlin imports in `finance-api/src/main/kotlin/.../Bar.kt` → blocked by **ktlint**
     8. `AKIAIOSFODNN7EXAMPLE` literal in `agent/main.py` → blocked by **gitleaks**
     9. `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` literal in any file → blocked by **gitleaks** (GitHub PAT pattern)
     10. `httpx.post(f"{SPRING_BASE_URL}/v1/internal/whoami", ...)` in `agent/foo.py` → blocked by **`scripts/lint-no-spring-httpx.sh`** (Gap-4)
  3. Run `make lint` → assert non-zero exit and **all 10 violations are attributed** to the correct tool in the output (FR-030).
  4. Run `git commit -am 'try violations'` → assert pre-commit blocks (FR-029).
  5. Remove violations → `make lint` exits 0.
  
  (SC-003, SC-006, FR-027, FR-029, FR-030 — explicitly omits Double-for-currency per the Anti-vibe deferral note above)

**Checkpoint**: US4 functional — pre-commit and `make lint` are the single shared source of lint truth (T055 ⇔ T010); a deliberate violation cannot reach a PR by accident.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end runbook validation, repo-root tooling marker, data-model housekeeping, and SC-001 timing confirmation.

- [x] T058 [P] Create `pyproject.toml` at repo root as a repo-wide tool marker (ruff and mypy config can live here instead of separate files; source of truth for `[tool.ruff]` and `[tool.mypy]` if not using separate files); ensure `make lint` still resolves config (plan.md project structure, SC-009)
- [x] T059 [P] Create `finance-api/src/test/kotlin/app/luci/finance/M2MKeyConfigUnitTest.kt`: given a PEM-encoded RSA public key, assert `JWKSet` contains exactly one key with correct `kty=RSA`, `use=sig`, `alg=RS256`, `kid` matching env var; given malformed PEM, assert `IllegalStateException` (validates key-loading bootstrap path in isolation; complements T044's integration assertion)
- [x] T060 Re-verify `specs/001-foundation-skeleton/data-model.md` §4 alignment with the JWT failure-marker pipeline implemented in T041. The 5th marker `m2m.jwt.iat_future` is already documented in data-model.md (T1 governance update landed alongside this revision of tasks.md). This task is verification-only: re-read §4 and confirm the implemented `JwtFailureEventListener` emits exactly those 5 markers and no others.
- [x] T061 Run full quickstart.md validation from §1 through §6 on a clean machine (or clean Docker volume): `make bootstrap` → `make up` → `make dev` → verify both `/health` green within 60 s (SC-002) → `make verify-m2m` → `make lint` → `docker stop luci_redis` → assert `/health` reports `redis=DOWN` within 10 s (SC-008) → `make reset` → confirm clean restart; record total wall-clock from `git clone` end to validate SC-001 ≤ 30 min

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Foundational — no other story dependencies
- **US2 (Phase 4)**: Depends on Foundational + US1 (services must boot cleanly) — the M2M auth layer builds on working service skeletons
- **US3 (Phase 5)**: Depends on Foundational + US1 + US2 (prod profile needs JWT config + health endpoints)
- **US4 (Phase 6)**: Depends on Foundational — linter configs are independent of service logic; can be done in parallel with US1/US2/US3 in a multi-developer scenario. T054 (`lint-no-spring-httpx.sh`) is referenced by T010, so US4 setup must merge before T010 is final.
- **Polish (Final Phase)**: Depends on all user stories complete; T060 (data-model.md update) depends on T041 specifically.

### User Story Dependencies

- **US1 (P1)**: After Foundational — no dependencies on US2/US3/US4
- **US2 (P2)**: After US1 (requires services to boot and /health to work)
- **US3 (P3)**: After US2 (prod profile must include JWT config)
- **US4 (P4)**: After Foundational — independently workable

### Within Each User Story

- **Tests are listed FIRST in every phase** and MUST be written and verified to FAIL before any implementation task in the same phase (TDD; constitution §IV; speckit.superb.tdd hook enforces this)
- Settings/config before routes (routes depend on settings)
- M2MKeyConfig (T036) before JwksController (T037) and WhoamiController (T038)
- SecurityConfig path-matchers (T022) before JWT resource server wiring (T040), before failure-marker pipeline (T041)
- Generated client (T034) before spring_client.py (T035) and test_whoami (T032 is RED; T032 stays red until T033–T041 land)

### Parallel Opportunities

- **Setup**: T002, T003, T004 run in parallel (different service dirs)
- **Foundational**: T007, T008, T009, T011, T012, T014, T015, T016 run in parallel after T006 (different files); T010 depends on T054 (US4) being defined but T010 can stub the no-httpx hook and be finalized when T054 lands
- **US1 tests**: T017, T018, T019 in parallel (different files); RED phase first
- **US1 impl**: T020, T021, T023, T024, T025, T027, T028 run in parallel (different files); T022 depends on T013 (Spring app boot); T026 depends on T023 (settings) + T014 (logging) + T024 (metrics) + T025 (health)
- **US2 tests**: T030, T031, T032 in parallel (different files)
- **US2 impl**: T033, T035, T039 run in parallel; T036 → T037 → T040 → T041 (M2MKeyConfig before JwksController before resource-server wiring before failure-marker)
- **US3 tests**: T042, T043, T044, T045 in parallel (different files)
- **US3 impl**: T046, T047, T048 run in parallel (different files); T049 is deletion-only
- **US4**: T050–T056 run in parallel (all different config files)
- **Polish**: T058, T059, T060 in parallel; T061 last

---

## Parallel Example: User Story 2

```bash
# RED phase — write these tests first (in parallel):
Task T030: WhoamiIntegrationTest.kt (Testcontainers, 7 equivalence classes including iat_future)
Task T031: WhoamiAuthFuzzTest.kt (parametrized, 100 cases, seeded RNG)
Task T032: test_skeleton.py::test_whoami_round_trip

# GREEN phase — implement in parallel where possible:
Task T033: agent/http/m2m_auth.py
Task T035: agent/http/spring_client.py
Task T039: finance-api/api/advice/GlobalExceptionHandler.kt

# Sequential chain (each depends on prior):
Task T034: codegen (depends on openapi.yaml from T005)
Task T036: M2MKeyConfig.kt          ← reads LUCI_M2M_PUBLIC_KEY_PEM, builds JWKSet
Task T037: JwksController.kt        ← needs JWKSet bean (T036)
Task T040: Update SecurityConfig (JWT resource server) ← needs T036
Task T038: WhoamiController.kt      ← needs T040
Task T041: JWT failure-marker pipeline (5 markers, AuthenticationEntryPoint + listener) ← needs T040
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup (T001–T005)
2. Complete Phase 2: Foundational (T006–T016) — **CRITICAL**
3. Write US1 tests (T017–T019) → verify they FAIL
4. Complete US1 implementation (T020–T029)
5. **STOP and VALIDATE**: `make bootstrap && make up && make dev` → both `/health` green; `FlywayBaselineTest` and `HealthEndpointIntegrationTest` pass
6. Proceed to US2 only after US1 checkpoint passes

### Incremental Delivery

1. Setup + Foundational → infra ready
2. US1 → green `/health` endpoints → local dev experience complete (SC-001 target)
3. US2 → authenticated M2M channel → contract seam proven (SC-005 target)
4. US3 → cloud parity → `SPRING_PROFILES_ACTIVE=prod` swap works (SC-004 target)
5. US4 → quality gates → lint/pre-commit live (SC-006 target)
6. Polish → full quickstart validation + data-model update (SC-001 timing proof)

### Parallel Team Strategy

With two developers after Foundational is done:
- **Dev A**: US1 (health endpoints, Testcontainers health tests, Flyway test)
- **Dev B**: US4 (lint configs, pre-commit, ktlint, gitleaks, no-httpx-to-Spring script — independent of service code)
- After US1 merges: Dev A picks up US2; Dev B picks up US3

---

## Notes

- `[P]` tasks target different files and have no dependency on other in-progress tasks in the same phase
- `[USN]` labels map tasks to user stories from spec.md for traceability
- Each user story is independently completable and testable
- Tests MUST be written first, verified to FAIL, then implementation makes them pass (TDD discipline, constitution §IV)
- The generated `agent/finance_api_client/` is committed to the repo (NOT gitignored — see T006) so drift is diff-visible in code review (R-6, resolves G2)
- `SecurityConfig.kt` is updated across three tasks (T022 whitelists health/metrics + forward-compat matcher for `/v1/users/**`; T040 adds JWT resource server; T047 adds `@Profile("prod")` bean with `hasIpAddress` matchers) — each update is additive, no backtracking
- The JWT failure-marker pipeline has **5 markers** (not 4): `missing`, `invalid_signature`, `expired`, `audience_mismatch`, `iat_future` — the 5th distinguishes "clock skew beyond TTL" from a plain "expired token" (spec Edge Case; Gap-3)
- Avoid: vague tasks, same-file conflicts within a phase, cross-story implementation dependencies that break story independence
- SC-009 constraint: zero new non-test source files outside `agent/`, `finance-api/`, `infra/`, `Makefile`, `.env.example`, config files, `scripts/`, and `openapi.yaml` — every task file listed above has an FR or research decision (R-x) justifying its existence
- **Anti-vibe rule deferrals** (per plan.md R-16): Spotbugs `no Double/Float for currency` and PMD `no raw stack traces from controllers` land with the first feature introducing currency-handling code. T057's violation suite enumerates only Day-0-enforceable rules.

---

## Review-Feedback Trace (this revision)

This revision of `tasks.md` resolves the following findings from `/speckit-superb-review` and the Gemini analysis report (`analysis_report.md.resolved`):

| Source | Finding | Resolution |
|---|---|---|
| gemini/G2 (HIGH) | `.gitignore` self-contradiction | T006 — generated client removed from gitignore; explicit "DO NOT add" comment |
| review/Gap-1 (R05) | Spring live-reload missing | T003 — `spring-boot-devtools` added (`developmentOnly`) |
| review/Gap-2 (R25) | Prod must refuse dev-key fallback | T036 (fail-fast in bean) + T044 (integration test) + T045 (Python-side mirror) |
| review/Gap-3 (R26) | Clock skew > TTL distinct log | T041 — 5th marker `m2m.jwt.iat_future`; T030 case; T060 data-model.md update |
| review/Gap-4 (R31/FR-002b) | No-httpx-to-Spring lint rule | T054 (`scripts/lint-no-spring-httpx.sh`) + T010 (wire into `make lint`) + T055 (pre-commit hook) |
| review/Gap-5 (R62/FR-028) | ktlint can't enforce no-Double-for-currency | Option B — deferred per R-16, documented in US4 deferral note, dropped from T057 |
| review/Gap-6 (R73/SC-005) | 100-request synthetic mix | T031 (`WhoamiAuthFuzzTest.kt`, parametrized, seeded, 100 cases) |
| review/Gap-7 (R74/SC-006) | 10-violation suite | T057 — expanded to 10 enumerated, Day-0-enforceable violations |
| review/T038-T044 placeholders | Vague "or" choices | T041 (concrete `AuthenticationEntryPoint` + `ApplicationListener`); T047 (`@Profile("prod")` + `hasIpAddress`); T044 (concrete `FailFastIntegrationTest.kt`) |
| review/TDD ordering | Tests after impl visually | Every phase now: **Tests** subsection above **Implementation** |
| gemini/A1 | Gradle wrapper pinned | T004 — Gradle 8.10.2 |
| gemini/B1 | Port collision | T010 — documented reliance on Docker / OS / Spring native error messages |
| gemini/C1 | T022 concurrency | T025 — explicit `asyncio.gather` + `asyncio.wait_for` per-probe timeout |
| gemini/C3 | `/v1/users/**` forward-compat | T022 + T040 — matcher gates `/v1/users/**` too |
| gemini/E1 | T042 lacks test-first | T042 — `test_settings.py` precedes implementation T046 |
| gemini/E2 | T043 lacks test-first | T043 — `ProdProfileSecurityTest.kt` precedes implementation T047 |
| gemini/E3 | T044 underspec | T044 — concrete `FailFastIntegrationTest.kt`; original placeholder T044 deleted |
| gemini/F1 | `make lint` command form | T010 — command forms verbatim match quickstart §6 |
| gemini/F2 | openapi-python-client compat | T002 — pinned `openapi-python-client>=0.21` |
| gemini/F3 | springdoc double-config | T013 owns the path properties; T021 (was T018) owns only the `GroupedOpenApi` bean |
| gemini/C2 | Health cache `0s` redundant | Rejected — explicit `0s` is defensive documentation, kept as-is in T020 |
| gemini/D1 | OTel deferral | Rejected — already authorized by FR-026d, no action |
| gemini/D2 | Idempotency-Key | Rejected — already correctly handled (lint rule lands; no state-mutating POST in scope) |

Total tasks: **61** (was 54; +7 net — 4 new tests for E1/E2/E3/Gap-2, 1 new fuzz test for Gap-6, 1 new lint script for Gap-4, 1 data-model housekeeping; original T044 placeholder removed and renumbered).
