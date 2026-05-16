# Research — Foundation Walking Skeleton

**Branch**: `001-foundation-skeleton` | **Date**: 2026-05-15 | **Spec**: [`spec.md`](spec.md)

The spec was heavily clarified before this phase (five `/speckit-clarify` rounds, see `spec.md#clarifications`), so most product-level unknowns are already resolved. This document records the remaining **technical choices** the spec deferred to `/speckit-plan`, with the alternatives that were considered and rejected. Every entry is load-bearing for at least one FR or SC in the spec.

---

## R-1 — JVM language for `finance-api/`

- **Decision**: **Kotlin** (JVM 21, Gradle Kotlin DSL).
- **Rationale**: Spec FR-001 and FR-028 explicitly call the service "Kotlin/Spring" and mandate **`ktlint`** (a Kotlin-only linter). The constitution permits "Kotlin/Java"; the spec narrows it. The Spec wins over the TDD's `.java` examples in §4.2 (a documentation inconsistency that the constitution authorises this spec to override).
- **Alternatives**: Java 21 (rejected: ktlint does not apply; the spec already wired the ktlint anti-vibe gate). Mixed Kotlin+Java (rejected: needless complexity in a skeleton).

## R-2 — Spring Boot version and starter set

- **Decision**: **Spring Boot 3.4.x** (latest GA at this writing; JDK 21 native baseline). Starters: `spring-boot-starter-web`, `-actuator`, `-data-redis`, `-jdbc`, `-security`, `-oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`, `micrometer-registry-prometheus`, `logstash-logback-encoder`, `flyway-core` + `flyway-database-postgresql`. JDBC (not JPA) is enough for the skeleton — `/health` uses Actuator's auto-configured `DataSourceHealthIndicator`, not entities.
- **Rationale**: Spring Boot 3.x is the only line supporting JDK 21 as a first-class baseline. Actuator + micrometer-registry-prometheus cover FR-024 and FR-026b. springdoc-openapi-starter-webmvc-ui covers FR-002a. spring-security-oauth2-resource-server covers FR-018 (JWT validation via `jwk-set-uri`).
- **Alternatives considered**: Spring Boot 3.3 (rejected: 3.4 is current GA, no breaking changes). Hand-rolled JWT filter with Nimbus only (rejected for the **inbound** path — Spring Security's resource server pipeline rejects unsigned requests before any controller runs, which FR-018 requires).

## R-3 — Spring JWT verification path

- **Decision**: **`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointed at Spring's own `/.well-known/jwks.json`**, served by a `@RestController` that returns the in-memory `JWKSet`. In local profile the URI is `http://localhost:8080/.well-known/jwks.json`; in prod it is the platform-internal URL of the same service. `NimbusJwtDecoder` fetches lazily on first JWT validation, so there is no startup self-loopback hazard.
- **Rationale**: Spec FR-019 explicitly mandates "Spring's own JWT verifier MUST consume the JWKS over the internal path." That sentence rules out the otherwise-tempting alternative of loading a PEM directly into `NimbusJwtDecoder.withPublicKey(...)` and skipping HTTP. Going through JWKS forces key-rotation discipline from day 0 and removes one class of "works locally, breaks in prod" surprise.
- **Alternatives**: Static PEM-backed `JwtDecoder` (rejected, see above). Hand-rolled `OncePerRequestFilter` with `Nimbus JWTProcessor` (rejected: more code than the auto-configured resource server, no win).

## R-4 — JWT signing in Python

- **Decision**: **PyJWT (`PyJWT[crypto]`)** for RS256 token minting. Algorithm pinned to `RS256` on every call site; no `none`/HS256 fallback.
- **Rationale**: PyJWT is the most widely used Python JWT library, has a stable RS256 API, and is the recommendation in the Auth0 / FastAPI ecosystem. `authlib` and `python-jose` are alternatives; `python-jose` is in low-maintenance mode (last release lagging), and `authlib` brings an OAuth client surface we do not need.
- **Alternatives**: `authlib` (rejected: too much surface area for a single signing helper). `python-jose` (rejected: maintenance signal). Hand-rolled signing with `cryptography` (rejected: reinvents a wheel and increases audit surface).

## R-5 — JWKS generation & key bootstrap (`make bootstrap`)

- **Decision**: `make bootstrap` calls a committed shell script (`scripts/bootstrap-keys.sh`) that:
  1. Checks for `LUCI_M2M_PRIVATE_KEY_PEM` in `.env.local`. If present and parseable as a 2048-bit RSA key, exits 0 (idempotent).
  2. Otherwise generates a 2048-bit RSA keypair with `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048`, writes the **PEM-encoded private half** to `.env.local` as `LUCI_M2M_PRIVATE_KEY_PEM=` (newlines escaped), writes the **PEM-encoded public half** to `.env.local` as `LUCI_M2M_PUBLIC_KEY_PEM=`, and emits a stable `kid` (`luci-m2m-<unix-epoch-of-creation>`) as `LUCI_M2M_KID=`.
  3. Both services read the same `.env.local` (Python via `pydantic-settings`, Spring via `spring.config.import=optional:file:.env.local[.properties]` or, more idiomatically, environment-variable export from the Makefile).
- **Rationale**: Spec FR-021 mandates a 2048-bit RS256 keypair, gitignored persistence of the private half, idempotent re-runs, and the public half placed "where Spring's local profile reads it." Storing both halves as env vars (PEM strings) — rather than as separate files — keeps the contract single-sourced and trivially injectable in DigitalOcean App Platform (which exposes secrets only as env vars). `openssl` is present on every supported developer platform; if absent the script aborts with a one-line error pointing at install instructions.
- **Alternatives**: Python `cryptography` library invoked from a uv-managed venv (rejected: requires venv before keypair, chicken-and-egg vs. `make bootstrap` itself installing uv). Separate `.pem` files under `~/.luci/keys/` (rejected: harder to inject into containerised dev/prod uniformly).

## R-6 — `openapi.yaml` placement and generated-client wiring

- **Decision**:
  - The committed `openapi.yaml` lives at **repository root** (`/openapi.yaml`) per TDD §17.3.
  - A `make codegen` target (also invoked by `make bootstrap`) runs `openapi-python-client generate --path openapi.yaml --output-path agent/finance_api_client --overwrite` and commits the generated directory.
  - The Python agent imports `from agent.finance_api_client import Client` and uses it for every Spring call. The ruff custom rule from the constitution (no hand-written `httpx` to Spring) is configured to flag any `httpx.AsyncClient`/`httpx.post` whose URL string targets `SPRING_BASE_URL` or `finance-api.luci.app` outside `agent/finance_api_client/`.
- **Rationale**: Spec FR-002b mandates regeneration by `make bootstrap` or an equivalent committed target. Putting the generated code in the repo (rather than regenerating per-CI run) makes "drift" diff-visible in code review, which is the same posture the constitution takes for prompts and migrations.
- **Alternatives**: Generate to a build directory and gitignore it (rejected: hides drift from PR review). Use `httpx-openapi` or hand-roll a thin wrapper (rejected: spec forbids it).

## R-7 — `/health` endpoint shape & wiring

- **Decision**:
  - **Spring**: enable Actuator's `health` endpoint, map it to `/health` (no `/actuator/` prefix) via `management.endpoints.web.base-path=/` plus `management.endpoints.web.path-mapping.health=health`. Set `management.endpoint.health.show-components=always` and `management.endpoint.health.show-details=always` so the unauthenticated probe gets the `components` map. The built-in `DataSourceHealthIndicator` and `RedisHealthIndicator` are auto-configured by the starters. Whitelist `/health`, `/metrics`, `/.well-known/**` in Spring Security.
  - **Python**: a hand-written FastAPI route `GET /health` that does two probes in parallel (`SELECT 1` against Postgres via `psycopg[binary,pool]`, `PING` against Redis via `redis-py`), each with a 1-second timeout, and assembles `{"status": "UP|DOWN", "components": {"db": {...}, "redis": {...}}}`. Aggregate `status` is `DOWN` if any component is `DOWN`; HTTP status code mirrors (`200` if `UP`, `503` if `DOWN`).
- **Rationale**: Spec FR-024 mandates the Actuator JSON shape; FR-023 mandates HTTP-code parity with `status`; FR-025 caps caching at 10 s — Actuator's default of "no cache" already satisfies this and we keep it explicit by *not* setting `management.endpoint.health.cache.time-to-live` (Actuator only caches when that property is set). FR-026 forbids LLM/third-party/write calls in `/health` — our two probes are read-only.
- **Alternatives**: `Spring Boot Health Indicator` returned through a custom controller (rejected: reinvents Actuator). `httpx` round-trip to Spring's `/health` as the Python probe (rejected: would couple Python's health to Spring's health; FR-026 wants per-service status).

## R-8 — Prometheus metrics endpoints

- **Decision**:
  - **Spring**: include `micrometer-registry-prometheus`; Actuator exposes the scrape endpoint. Remap from the default `/actuator/prometheus` to `/metrics` via `management.endpoints.web.path-mapping.prometheus=metrics` so both services share the same path.
  - **Python**: use `prometheus_client` directly and mount `GET /metrics` on the FastAPI app using `prometheus_client.make_asgi_app()`. Bind the default process/runtime collectors plus a `Counter("http_requests_total", labels=["method","route","status"])` middleware that increments per response.
- **Rationale**: FR-026b allows either form, but path parity simplifies the cloud scrape config (`scrape_configs:` can match `/metrics` on both services with one rule). Both forms expose the standard text-format exposition without auth (FR-026c gating is platform-network-level, not in-service).
- **Alternatives**: `prometheus-fastapi-instrumentator` (rejected for now: a single counter + default collectors is enough for the skeleton; an extra dep adds nothing). Keeping Spring on `/actuator/prometheus` (rejected: path divergence for no benefit).

## R-9 — Structured JSON logging

- **Decision**:
  - **Python**: `structlog` with `structlog.processors.JSONRenderer()` and `structlog.contextvars.merge_contextvars` for request-scope context. Stdlib `logging` is forwarded into structlog via `structlog.stdlib.recreate_defaults()`. Banned: `print()` (already a ruff rule in the constitution).
  - **Spring**: `logstash-logback-encoder` configured in `logback-spring.xml`. Default appender writes a single JSON object per line to stdout with `@timestamp`, `level`, `logger`, `message`, `traceId` (from MDC), plus a static `service: finance-api` field. Banned: `System.out.println` (PMD rule, follow-on).
- **Rationale**: TDD §11.1 names exactly these two libraries; spec FR-026a requires the exact field set both libraries already emit by default or with one config line. structlog handles the `trace_id` propagation problem cleanly via context-vars; logstash-logback-encoder reads `traceId` from Logback's MDC, which Spring Security and any future OTel SDK populate.
- **Alternatives**: `python-json-logger` (rejected: less ergonomic context handling than structlog; the spec's "e.g." mentions both, but TDD specifies structlog). Logback's built-in JSON encoder via Boot 3.4 (acceptable as a future swap, but logstash-logback-encoder is the better-documented option for the field set FR-026a demands).

## R-10 — Docker Compose location and Make integration

- **Decision**: One compose file at **`infra/docker-compose.yml`** (TDD §17.3). The Makefile invokes it from the repo root with the explicit `-f` flag so "from-root ergonomics" is preserved without a thin root wrapper file:

  ```make
  COMPOSE := docker compose -f infra/docker-compose.yml --project-name luci

  up:    ; @$(COMPOSE) up -d --wait
  reset: ; @$(COMPOSE) down -v && $(MAKE) up
  ```

- **Rationale**: Spec A-1 deferred the choice. A thin root `docker-compose.yml` whose only job is `include: [infra/docker-compose.yml]` adds a second file to keep in sync for no functional gain. The `--project-name luci` flag, combined with `container_name: luci_*` declarations on each service, satisfies the spec's "container names MUST be prefixed `luci_*`" edge-case requirement.
- **Alternatives**: Root-level compose (rejected: violates TDD §17.3). Two files merged with `include` (rejected: adds drift surface).

## R-11 — Compose services & images for the skeleton

- **Decision**: Postgres (`ghcr.io/tembo-io/pg-pgmq:pg15`, exposed as `luci_postgres`) and Redis (`redis:7-alpine`, exposed as `luci_redis`). **MockServer and MinIO are deferred** — neither is needed for any FR in this feature. The compose file declares an `observability` profile that will hold Langfuse / Prometheus collectors when a later feature wires them in (per TDD §18.1 "optional" services).
- **Rationale**: Spec FR-006 requires *only* Postgres-with-pgvector and Redis; MockServer/MinIO are explicitly marked "MAY." Skeleton stays minimal; downstream features can add services without restructuring this file.
- **Alternatives**: Bring in MockServer now for symmetry with TDD §18.1 (rejected: nothing in this feature exercises it, and FR-009 cares about config surface, not running mocks).

## R-12 — Flyway baseline migration

- **Decision**: `finance-api/src/main/resources/db/migration/V1__baseline.sql`:

  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;

  -- Skeleton marker. Verifiable from a query:
  --   SELECT description FROM pg_description
  --   WHERE objoid = 'flyway_schema_history'::regclass AND objsubid = 0;
  COMMENT ON TABLE flyway_schema_history IS
      'Luci baseline migration applied. Walking skeleton — no domain tables yet.';
  ```

  No `_skeleton_marker` table is created — a `COMMENT ON TABLE` is identical in spirit, leaves no schema residue to retire, and is trivially verifiable from a query (FR-014).
- **Rationale**: Spec FR-014 lets the marker be "a `_skeleton_marker` table, a no-op stored function, *or* a comment on `flyway_schema_history`." The comment is the cheapest of the three; the constitution principle "prefer deletion over permanence" pushes us away from a marker table that would later need to be ripped out. `CREATE EXTENSION IF NOT EXISTS vector` handles both the local (`pg-pgmq` image, extension preinstalled) and Supabase (extension needs explicit enabling) paths idempotently.
- **Alternatives**: Marker table (rejected: residue). No-op stored function (rejected: residue + harder to verify than a `COMMENT`).

## R-13 — `make dev` runner (live reload for both services)

- **Decision**: A bash-driven `make dev` target that backgrounds `uvicorn agent.main:app --reload --host 0.0.0.0 --port 8000` and `./gradlew :finance-api:bootRun` in parallel, traps SIGINT to kill both. **No tmux dependency** for MVP.
- **Rationale**: TDD §18.6 suggests tmux; adopting tmux means making it a `make bootstrap` prerequisite and writing the layout glue. The spec's Story 1 AS-3 cares only that both services are running with live-reload — it does not mandate panes. A flat backgrounded runner satisfies the AS in 5 lines of Make; tmux can land as a polish in a follow-on.
- **Alternatives**: `concurrently` (Node) (rejected: adds Node toolchain). `foreman`/`hivemind` (rejected: extra dep for no benefit). tmux (rejected for now, may revisit).

## R-14 — Pre-commit framework and hook set

- **Decision**: **`pre-commit`** (Python tool) driving:
  - `ruff` (lint + format) — `astral-sh/ruff-pre-commit` revisioned hook.
  - `mypy` — local hook invoking `uv run mypy --strict agent/`.
  - `ktlint` — `pinterest/ktlint` pre-commit hook (or `jlleitschuh/ktlint-gradle` via `./gradlew ktlintCheck` as the canonical entry point, mirrored as a pre-commit local hook so `make lint` and the hook share a source of truth).
  - `gitleaks` — `gitleaks/gitleaks` pre-commit hook with `--no-git --staged` (staged-only, plus a full-tree scan from `make lint`).
- **Rationale**: Spec FR-029, FR-030, and FR-031 require the same configuration to be reachable from both `make lint` and `git commit` (no divergence); `pre-commit` is the canonical orchestrator. ktlint via Gradle keeps the Gradle build the single Kotlin lint entry point.
- **Alternatives**: `lefthook` / `husky` (rejected: introduces another runtime; pre-commit is already the Python ecosystem default and works for non-Python hooks too).

## R-15 — Python runtime, deps, and tooling matrix

- **Decision**: Python 3.12 managed by **`uv`** (`uv venv`, `uv pip install`, `uv run`). `pyproject.toml` declares:

  | Group | Packages |
  |---|---|
  | runtime | `fastapi`, `uvicorn[standard]`, `pydantic`, `pydantic-settings`, `httpx`, `pyjwt[crypto]`, `cryptography`, `psycopg[binary,pool]`, `redis>=5`, `structlog`, `prometheus-client` |
  | dev | `ruff`, `mypy`, `pytest`, `pytest-asyncio`, `respx`, `openapi-python-client` |

  `ruff.toml` (or `[tool.ruff]` in `pyproject.toml`) bans `print` (`T20`), enforces `Decimal`-for-currency via the `flake8-no-pep8-naming` rule set + a custom check (follow-on if not feasible out of the box). `mypy.ini` enables `strict = True`, `disallow_any_explicit = True`.
- **Rationale**: Matches AGENTS.md anti-vibe rules. `uv` is the TDD-named installer (§18.6). `psycopg` v3 with async pool is the modern, well-supported Postgres driver for FastAPI's event loop.
- **Alternatives**: `asyncpg` (rejected: pool ergonomics weaker for a one-shot probe). `pip-tools` (rejected: `uv` is the named choice).

## R-16 — Kotlin runtime, deps, and tooling matrix

- **Decision**: JDK 21 (Temurin), Kotlin 2.0.x, Gradle Wrapper (8.x). `finance-api/build.gradle.kts`:

  | Type | Dependencies |
  |---|---|
  | starter | `spring-boot-starter-web`, `-actuator`, `-data-redis`, `-jdbc`, `-security`, `-oauth2-resource-server` |
  | observability | `io.micrometer:micrometer-registry-prometheus`, `net.logstash.logback:logstash-logback-encoder` |
  | docs | `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
  | jwt | `com.nimbusds:nimbus-jose-jwt` (transitively present via Spring Security; declared explicitly so `JWKSet` API is on the compile classpath for the JWKS endpoint) |
  | db | `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql` |
  | test | `spring-boot-starter-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `com.redis:testcontainers-redis` |

  Spotbugs and PMD are scaffolded but **not enforced yet** for the skeleton (FR-028 names ktlint explicitly; the Spotbugs `Double`-for-currency rule lands with the first PR that handles currency). ktlint is wired via `org.jlleitschuh.gradle.ktlint`.
- **Rationale**: Matches FR-028 (ktlint mandatory now), FR-002a (springdoc), FR-018 (resource server), FR-024 (actuator + Redis health indicator), FR-026b (micrometer-registry-prometheus), FR-026a (logstash encoder).
- **Alternatives**: Maven (rejected: TDD and team familiarity favour Gradle). JPA starter (rejected: no entities in the skeleton).

## R-17 — Spring profiles and configuration surface

- **Decision**: Two profile files:
  - `application.yml` — common defaults (Actuator paths, springdoc paths, JWT issuer/audience constants, health caching off).
  - `application-local.yml` — `localhost:5432`/`localhost:6379` URLs; `jwk-set-uri: http://localhost:8080/.well-known/jwks.json`; `JWKS` reachable from any origin (since the only listener is the local dev box).
  - `application-prod.yml` — placeholders pulled from environment variables (`${LUCI_DB_URL}`, `${LUCI_REDIS_URL}`, `${LUCI_JWT_ISSUER}`, etc.); `jwk-set-uri` is the internal service URL; Spring Security path matcher restricts `/.well-known/**`, `/v1/internal/**`, and `/metrics` to the internal network/allow-list (FR-019, FR-020, FR-026c).
- **Rationale**: FR-009/FR-013 mandate "no code change to retarget." Two YAML files behind `SPRING_PROFILES_ACTIVE` is the canonical Spring Boot way. The prod gating of `/.well-known/**` and `/v1/internal/**` lives in a single `SecurityConfig` whose path matchers consult the active profile (`@Profile("prod")` bean replaces the local one).
- **Alternatives**: One YAML with conditional sections (rejected: harder to read, easier to leak local values into prod). External Spring Cloud Config server (rejected: massive overkill).

## R-18 — Environment variable manifest

- **Decision**: A single `.env.example` at the repo root listing every variable consumed by either service, grouped:

  ```dotenv
  # ── Profile & service IDs ────────────────────────────────────
  LUCI_ENV=local                       # local | prod (mirrored to SPRING_PROFILES_ACTIVE)
  SPRING_PROFILES_ACTIVE=local

  # ── Database (Postgres 15 + pgvector) ────────────────────────
  LUCI_DB_URL=jdbc:postgresql://localhost:5432/luci
  LUCI_DB_USER=postgres
  LUCI_DB_PASSWORD=luci_local

  # Python uses the libpq URL form (psycopg / asyncpg):
  LUCI_DB_DSN=postgresql://postgres:luci_local@localhost:5432/luci

  # ── Cache (Redis 7) ──────────────────────────────────────────
  LUCI_REDIS_URL=redis://localhost:6379/0

  # ── M2M JWT (RS256) ──────────────────────────────────────────
  LUCI_M2M_ISSUER=python-agent.luci.app
  LUCI_M2M_AUDIENCE=finance-api.luci.app
  LUCI_M2M_KID=                          # set by make bootstrap
  LUCI_M2M_PRIVATE_KEY_PEM=              # set by make bootstrap (Python signs)
  LUCI_M2M_PUBLIC_KEY_PEM=               # set by make bootstrap (Spring verifies)
  LUCI_M2M_JWKS_URL=http://localhost:8080/.well-known/jwks.json

  # ── Inter-service ────────────────────────────────────────────
  SPRING_BASE_URL=http://localhost:8080

  # ── Observability (skeleton scope only) ──────────────────────
  LUCI_LOG_LEVEL=INFO
  ```

  `.env.local` is `.gitignored` and is the file `make bootstrap` writes the keypair into.
- **Rationale**: FR-011 mandates the manifest. Grouping is from the FR's wording ("by domain"). The duplicate DB URL (JDBC for Spring, libpq for Python) is unavoidable — drivers consume different forms; one fact, two encodings.
- **Alternatives**: A single shared URL form parsed by each side (rejected: JDBC URLs are not libpq URLs; the conversion is one line of doc and zero lines of code).

## R-19 — Container naming and port plan

- **Decision**: `container_name: luci_postgres`, `luci_redis` (plus future `luci_mp_mock`, `luci_minio`, `luci_langfuse`). Compose project name pinned to `luci` (`--project-name luci`). Default port plan:

  | Service | Host port | Why |
  |---|---|---|
  | Postgres | 5432 | Default; conflict surfaces as a `make up` error per spec edge case |
  | Redis | 6379 | Default |
  | Spring | 8080 | Default; TDD §18.6 |
  | Python | 8000 | Uvicorn default; TDD §18.6 |

- **Rationale**: Spec edge case "ports already bound" wants a human-readable error. Docker Compose's "Bind for 0.0.0.0:5432 failed: port is already allocated" satisfies that out of the box; no Makefile preflight needed.
- **Alternatives**: Non-default ports (rejected: surprising; the spec explicitly calls out 5432/6379/8080/8000 as the expected defaults in its edge cases).

---

## NEEDS CLARIFICATION — status

**None remain.** The five spec clarifications and the eighteen decisions above resolve every "NEEDS CLARIFICATION" the plan template would otherwise raise. The Constitution Check in `plan.md` is therefore evaluated against concrete choices, not placeholders.
