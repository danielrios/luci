# Quickstart — Foundation Walking Skeleton

**Branch**: `001-foundation-skeleton` | **Date**: 2026-05-15 | **Spec**: [`spec.md`](spec.md)

This is the developer onramp the spec references at SC-001 ("≤ 30 minutes from `git clone` to a green local stack"). The exact same artefacts run against Supabase + a remote Redis by changing env vars only (Story 3, SC-004).

The steps assume you are starting on a **clean machine** with the prerequisites below installed. Times are wall-clock against an SSD-backed developer laptop with reasonable home-internet bandwidth.

---

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| Docker Engine | ≥ 24 | Runs the local Postgres and Redis containers. |
| JDK 21 | Temurin (or equivalent) | Runs the Spring Boot Finance API. The Gradle wrapper is committed. |
| Python | 3.12 | Runs the Python agent. `uv` installs the venv during bootstrap. |
| `openssl` | 1.1+ or 3.x | `make bootstrap` uses it to generate the local M2M RSA keypair. Present on every supported developer platform. |
| `git` | any modern | Required for `git clone` and pre-commit. |

You do **not** need to install: `uv`, `pre-commit`, `openapi-python-client`, `ktlint`, `gitleaks`, `mypy`, `ruff`. `make bootstrap` provisions all of them.

You do **not** need: a Supabase project, a remote Redis instance, a Telegram bot token, or a Vertex AI account — none are touched by the skeleton.

---

## 1 — Clone and bootstrap (≤ 5 min)

```bash
git clone https://github.com/danielriosdev/luci.git
cd luci
make bootstrap
```

`make bootstrap` is idempotent (Story 1 AS-1, FR-021). On a clean machine it:

1. Installs `uv`, creates `agent/.venv`, and `uv pip install`s Python deps from `agent/pyproject.toml`.
2. Verifies `JAVA_HOME` resolves to a JDK 21; primes the Gradle wrapper cache.
3. Installs `pre-commit` and runs `pre-commit install` so `ruff`, `mypy`, `ktlint`, and `gitleaks` fire on every `git commit`.
4. Copies `.env.example` → `.env.local` if `.env.local` is absent.
5. Calls `scripts/bootstrap-keys.sh`, which **generates a 2048-bit RS256 keypair** if one is not already present in `.env.local`, writing the PEM-encoded private half, the PEM-encoded public half, and a stable `kid` (`luci-m2m-<unix-epoch>`) (R-5, FR-021).
6. Runs `make codegen` once, regenerating `agent/finance_api_client/` from the committed `openapi.yaml` (FR-002b).

Re-running `make bootstrap` after a successful first run is a no-op for steps 4 and 5; the existing keypair is preserved.

**Verify**:

```bash
grep '^LUCI_M2M_KID' .env.local        # → LUCI_M2M_KID=luci-m2m-<digits>
ls agent/finance_api_client/             # → generated client present
```

## 2 — Bring up local infrastructure (≤ 3 min on a warm Docker image cache)

```bash
make up
```

`make up` runs `docker compose -f infra/docker-compose.yml --project-name luci up -d --wait`. Compose blocks on each service's `healthcheck` so the command exits 0 **only when all required containers are healthy** (FR-007). Then Spring's first `bootRun` (next step) applies the Flyway baseline.

**Verify**:

```bash
docker ps --filter "name=luci_" --format "table {{.Names}}\t{{.Status}}"
# luci_postgres    Up (healthy)
# luci_redis       Up
```

If a port is already bound on your machine, Compose emits the standard `Bind for 0.0.0.0:5432 failed: port is already allocated` error and `make up` exits non-zero (FR-007, spec edge case "ports already bound").

## 3 — Run the services with live reload (≤ 2 min to green `/health`)

```bash
make dev
```

Backgrounds both services with their native live-reload mechanisms (R-13, FR-005, Story 1 AS-3): `uvicorn agent.main:app --reload --host 0.0.0.0 --port 8000` and `./gradlew :finance-api:bootRun`. Both register SIGINT handlers so `Ctrl-C` brings the whole stack down cleanly.

On first run, Spring's startup applies the Flyway baseline migration:

```text
o.f.c.i.l.s.LogbackLogCreator : Migrating schema "public" to version "1 - baseline"
o.f.c.i.l.s.LogbackLogCreator : Successfully applied 1 migration to schema "public"
```

(SC-007: subsequent restarts emit `Schema "public" is up to date. No migration necessary.`)

**Verify** in a second terminal:

```bash
curl -sf http://localhost:8080/health | jq
# { "status": "UP",
#   "components": {
#     "db":    { "status": "UP", ... },
#     "redis": { "status": "UP", ... } } }

curl -sf http://localhost:8000/health | jq
# Same shape (FR-024).
```

Both endpoints MUST return `200 OK` within 60 seconds of `make dev` (SC-002).

## 4 — Verify the M2M auth seam (Story 2)

```bash
make verify-m2m
```

This invokes a committed integration test (`agent/tests/test_skeleton.py::test_whoami_round_trip`) that:

1. Mints a fresh RS256 JWT with the keypair from `.env.local`.
2. Calls `POST http://localhost:8080/v1/internal/whoami` via the **generated** `httpx` client (FR-002b).
3. Asserts `200 OK` and that `subject`, `user_id`, `intent`, `trace_id` echo back (Story 2 AS-1, SC-005).
4. Re-runs the call with no `Authorization` header → asserts `401` (Story 2 AS-2).
5. Re-runs with a JWT signed by a throwaway key → asserts `401` (Story 2 AS-3).

If you want to inspect manually:

```bash
# JWKS — should return the single RSA key the bootstrap script generated:
curl -sf http://localhost:8080/.well-known/jwks.json | jq
```

## 5 — Try a recovery cycle (spec edge cases)

Failure-mode rehearsal — none of these should leave the local stack in a poisoned state.

```bash
# (a) Stop Postgres mid-flight. /health on Spring must report db=DOWN within ~10s.
docker stop luci_postgres
curl -i http://localhost:8080/health        # → 503; "components.db.status": "DOWN"
docker start luci_postgres

# (b) Wipe everything and start over. Single deterministic command (FR-008).
make reset                                  # docker compose down -v && make up
```

## 6 — Run quality gates locally (Story 4)

```bash
make lint
```

Runs `ruff check` + `mypy --strict` on `agent/`, `./gradlew :finance-api:ktlintCheck` on `finance-api/`, and `gitleaks detect --source .` over the working tree. Exits 0 on a clean tree (SC-003: ≤ 30 s warm); exits non-zero if any tool reports findings, but still runs all tools so a Python lint failure does not hide a Kotlin one (FR-030).

To **prove** the gate fires: introduce a `print()` in any file under `agent/`, then `git commit` — `pre-commit` blocks the commit with the `ruff T201` diagnostic (Story 4 AS-2).

## 7 — Cloud parity dry-run (Story 3, optional)

Only run this when a Supabase project and a remote Redis instance exist. With `make up` and `make dev` stopped:

```bash
# In a separate shell, point env vars at the cloud:
export SPRING_PROFILES_ACTIVE=prod
export LUCI_ENV=prod
export LUCI_DB_URL='jdbc:postgresql://db.<project>.supabase.co:5432/postgres?sslmode=require&user=postgres&password=<pw>'
export LUCI_DB_DSN='postgresql://postgres:<pw>@db.<project>.supabase.co:5432/postgres?sslmode=require'
export LUCI_REDIS_URL='rediss://default:<pw>@<host>.upstash.io:6379'
export LUCI_M2M_JWKS_URL='http://localhost:8080/.well-known/jwks.json'   # still self-loopback, but profile-gated
# JWT keys: still the local ones for this dry-run; cloud keys are secrets-manager-injected in real deploy.

make dev
```

Expected (Story 3 AS-1 / AS-2 / SC-007):

- Spring boots; Flyway logs `Successfully applied 1 migration`.
- `curl http://localhost:8080/health` → `200 OK`, `db=UP`, `redis=UP`.
- Re-start: Flyway logs `Schema "public" is up to date. No migration necessary.`

If any of the env vars above is missing or malformed, Spring (and Python) **fail fast at boot** with a single named-variable error (FR-010, Story 3 AS-4) — they do not start in a degraded state.

---

## Troubleshooting cheatsheet

| Symptom | Cause | Action |
|---|---|---|
| `make up` exits with `bind for 0.0.0.0:5432 failed` | Local Postgres is already running on the host. | Stop the host Postgres or remap the port in `infra/docker-compose.yml`. |
| `/health` on Spring returns `503` with `db=DOWN` shortly after a `make up` | Compose's healthcheck declared `postgres` healthy but Spring's pool is still warming. | Wait 5–10 s and retry; if it persists, `docker logs luci_postgres`. |
| `make bootstrap` says `LUCI_M2M_PRIVATE_KEY_PEM looks malformed` | An earlier run truncated the PEM (likely you edited `.env.local` by hand). | Delete the three `LUCI_M2M_*KEY_PEM*` and `LUCI_M2M_KID` lines from `.env.local` and re-run `make bootstrap` — it regenerates. |
| Spring rejects every JWT with `401` and the log says `audience_mismatch` | Python and Spring have different `LUCI_M2M_AUDIENCE` values (probably because `.env.local` is stale). | `make bootstrap` to re-seed, restart both services. |
| `pre-commit` hangs on first commit | First-run hook downloads (ruff, gitleaks). | Wait through the initial download; subsequent commits are fast. |
| `make reset` complains about an in-use volume | A previous `make dev` is still running. | `pkill -f bootRun; pkill -f uvicorn; make reset`. |
