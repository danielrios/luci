# Data Model — Foundation Walking Skeleton

**Branch**: `001-foundation-skeleton` | **Date**: 2026-05-15 | **Spec**: [`spec.md`](spec.md)

The skeleton intentionally introduces **no domain tables** (FR-032). The only database artefact is the Flyway baseline (`V1__baseline.sql`), captured in §6 below. The remaining "entities" listed by the spec are **infrastructure-level shapes** — JSON bodies, configuration manifests, and short-lived tokens — that the skeleton produces or consumes. Each is documented here as a *shape contract* so downstream features can extend without re-discovery.

---

## 1. Workspace Profile

A logical binding of "where am I running" to a concrete dependency set.

| Field | Type | Required | Source of truth | Notes |
|---|---|---|---|---|
| `service` | `agent` \| `finance-api` | yes | code (constant) | Static, set per service at startup. |
| `profile_name` | `local` \| `prod` | yes | env: `LUCI_ENV` (Python) / `SPRING_PROFILES_ACTIVE` (Spring) | The two values are kept in lockstep by `.env.local` / Make targets. |
| `db_url` | URL | yes | env: `LUCI_DB_URL` (JDBC) or `LUCI_DB_DSN` (libpq) | Forms differ because drivers differ (R-18). |
| `redis_url` | URL | yes | env: `LUCI_REDIS_URL` | `rediss://` for TLS-required vendors. |
| `spring_base_url` | URL | yes (Python only) | env: `SPRING_BASE_URL` | Consumed by the generated `httpx` client. |
| `jwks_url` | URL | yes (Spring only) | env: `LUCI_M2M_JWKS_URL` | Spring's own verifier fetches its own JWKS (R-3, FR-019). |

**Identity**: `(service, profile_name)`. Exactly one profile is active per process; switching is a restart.

**Validation rules**:

- Every required field MUST be present at startup. Missing → fail-fast with a single named-variable error (FR-010, Story 3 AS-4).
- In `prod`, `db_url` MUST require TLS (`sslmode=require` or driver equivalent) and `redis_url` MUST be `rediss://`. Plaintext `prod` is a misconfiguration the skeleton rejects at boot (Story 3 AS-4, FR-012).
- No silent defaults for any field that differs between `local` and `prod` (FR-010).

**State transitions**: None. Profile is process-immutable.

---

## 2. Environment Variable Manifest

The canonical list of variables consumed by either service. Source of truth is `.env.example` at the repository root.

| Variable | Owner | Required? | Skeleton purpose |
|---|---|---|---|
| `LUCI_ENV` | both | yes | Selects logical profile in Python; mirrored to `SPRING_PROFILES_ACTIVE`. |
| `SPRING_PROFILES_ACTIVE` | Spring | yes | Selects YAML profile in Spring (`local` \| `prod`). |
| `LUCI_DB_URL` | Spring | yes | JDBC URL for HikariCP. |
| `LUCI_DB_USER` | Spring | yes (local) | Hikari user. In `prod`, may be embedded in the URL. |
| `LUCI_DB_PASSWORD` | Spring | yes (local) | Hikari password. In `prod`, secrets-manager-injected. |
| `LUCI_DB_DSN` | Python | yes | libpq-style URL for `psycopg`. |
| `LUCI_REDIS_URL` | both | yes | Single URL form for both clients. |
| `LUCI_M2M_ISSUER` | both | yes | `iss` claim minted by Python; verified by Spring. |
| `LUCI_M2M_AUDIENCE` | both | yes | `aud` claim minted by Python; verified by Spring. |
| `LUCI_M2M_KID` | both | yes (after bootstrap) | Stable JWK key-id, written by `make bootstrap`. |
| `LUCI_M2M_PRIVATE_KEY_PEM` | Python | yes (after bootstrap) | RSA private half for token signing. Gitignored. |
| `LUCI_M2M_PUBLIC_KEY_PEM` | Spring | yes (after bootstrap) | RSA public half published via JWKS. Gitignored. |
| `LUCI_M2M_JWKS_URL` | Spring | yes | URL the JWT verifier fetches keys from (R-3). |
| `SPRING_BASE_URL` | Python | yes | Target for the generated `httpx` client. |
| `LUCI_LOG_LEVEL` | both | no (defaults to `INFO`) | Forwarded to structlog (Python) and Logback (Spring). |

**Validation rules**:

- `.env.example` MUST list every variable above with safe placeholder values, grouped by domain (FR-011).
- `.env.local` MUST be `.gitignored` and MUST be created by `make bootstrap`.
- A CI check enforcing "every variable in `.env.example` is read by at least one service, and every read variable appears in `.env.example`" may be added later; the skeleton MUST keep the manifest as the single committed reference (FR-011).

**Identity**: variable `name`. Names are SCREAMING_SNAKE_CASE; the `LUCI_` prefix segregates Luci-owned variables from third-party ones (e.g., `SPRING_PROFILES_ACTIVE`, `JAVA_HOME`).

---

## 3. Service Health Report

The JSON body returned by `GET /health` on either service. Shape is the Spring Boot Actuator `Health` schema (FR-024).

```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "isValid()" } },
    "redis": { "status": "UP", "details": { "version": "7.2.4" } }
  }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `status` | `"UP"` \| `"DOWN"` | yes | Top-level aggregate. `DOWN` if **any** component is `DOWN` (FR-024). |
| `components` | object | yes | Keyed by dependency name. |
| `components.db` | object | yes | `db` is the Actuator-default key the `DataSourceHealthIndicator` produces; Python mirrors it by hand. |
| `components.redis` | object | yes | Same — Actuator's `RedisHealthIndicator` uses `redis` as the key. |
| `components.<name>.status` | `"UP"` \| `"DOWN"` | yes | Per-component status. |
| `components.<name>.details` | object | no | Implementation-defined per indicator. Allowed but not required (FR-024). |

**HTTP coupling**:

- Aggregate `UP` → HTTP `200 OK` (FR-023).
- Aggregate `DOWN` → HTTP `503 Service Unavailable` (FR-023 "non-2xx").
- Unauthenticated (FR-023).
- No caching beyond 10 s (FR-025); the skeleton uses Actuator's "no cache" default, which means each scrape is a fresh probe.

**Validation rules**:

- MUST NOT include synthetic transactions, LLM calls, third-party API calls, or DB writes (FR-026). The two probes are `SELECT 1` and Redis `PING`.
- The Python response shape MUST match Spring's response shape **byte-for-byte at the `status` + `components.db.status` + `components.redis.status` level**. Extra fields are permitted on either side but not required (FR-024).

---

## 4. M2M Token (RS256 JWT)

The short-lived bearer Python signs and Spring verifies on every `/v1/internal/*` call.

| Claim | Type | Required | Source | Verified by |
|---|---|---|---|---|
| `iss` | string | yes | env `LUCI_M2M_ISSUER` (`python-agent.luci.app`) | Spring (`oauth2.resourceserver.jwt.issuer-uri`-style match) |
| `aud` | string | yes | env `LUCI_M2M_AUDIENCE` (`finance-api.luci.app`) | Spring (`OAuth2TokenValidator<Jwt>` with audience predicate) |
| `sub` | string | yes | constant `service:python-agent` | Spring (echoed back by `/whoami`) |
| `exp` | epoch seconds | yes | `iat + 15·60` (TTL ≤ 15 min, FR-017) | Spring (with ≤ 60 s leeway, FR-022) |
| `iat` | epoch seconds | yes | mint time | — |
| `user_id` | UUID string | yes | derived by Python from the Telegram event upstream | Spring (echoed back; not independently authenticated, TDD §8.2) |
| `intent` | string | yes | classified intent (skeleton: literal `"whoami"`) | Spring (echoed back) |
| `trace_id` | string | yes | OpenTelemetry trace-id or `uuid4().hex` if no upstream trace | Spring (echoed back; propagated into MDC) |

**JOSE header**:

| Header | Value | Notes |
|---|---|---|
| `alg` | `"RS256"` | Pinned. No `none`/HS256 path exists in code. |
| `kid` | `LUCI_M2M_KID` | Stable across the keypair's lifetime; rotated quarterly (out of scope for this feature). |
| `typ` | `"JWT"` | Default. |

**Lifecycle**: not persisted. Minted per outbound call; discarded after the request returns.

**Failure classes** (must produce distinct log entries, FR-022; spec Edge Case "Clock skew exceeds JWT TTL" requires the 5th class):

| Class | Spring response | Log marker |
|---|---|---|
| Missing `Authorization` header | `401` | `m2m.jwt.missing` |
| Signature invalid (unknown `kid` or wrong key) | `401` | `m2m.jwt.invalid_signature` |
| Expired (`exp + leeway < now`) | `401` | `m2m.jwt.expired` |
| Audience mismatch | `401` | `m2m.jwt.audience_mismatch` |
| `iat` / `nbf` in the future beyond leeway (`iat - leeway > now`) | `401` | `m2m.jwt.iat_future` |

The 5th marker is **distinct from `m2m.jwt.expired`** by design — they represent symmetric failure modes (clock-skew-positive vs clock-skew-negative). Nimbus's `JwtTimestampValidator` reports them as different `OAuth2Error.description` values, which the `JwtFailureEventListener` (tasks.md T041) inspects to choose the correct marker.

---

## 5. JWKS Endpoint

The public-key publication surface on Spring. Shape is the IETF JWK Set (RFC 7517).

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "luci-m2m-1747315200",
      "n": "<base64url modulus>",
      "e": "AQAB"
    }
  ]
}
```

| Field | Type | Required | Source |
|---|---|---|---|
| `keys[]` | array of JWK | yes | In-memory `JWKSet` constructed from `LUCI_M2M_PUBLIC_KEY_PEM` at boot |
| `keys[].kty` | `"RSA"` | yes | constant |
| `keys[].use` | `"sig"` | yes | constant |
| `keys[].alg` | `"RS256"` | yes | constant |
| `keys[].kid` | string | yes | `LUCI_M2M_KID` |
| `keys[].n`, `keys[].e` | base64url | yes | derived from the RSA public key |

**Network exposure** (FR-019):

| Profile | Exposure |
|---|---|
| `local` | Reachable on `localhost` without authentication. |
| `prod` | Reachable only from the platform-internal network or behind the same allow-list as `/v1/internal/*`. NOT reachable from the public internet. |

**Lifecycle**: served on every request; backing key set is built once at boot. Key rotation (quarterly) is out of scope for this feature — the JWKS structure already supports multi-key responses for the overlap window.

---

## 6. Baseline Schema Migration

The single Flyway script that defines "an empty Luci database."

**Path**: `finance-api/src/main/resources/db/migration/V1__baseline.sql`

**Contents** (final form, FR-014):

```sql
CREATE EXTENSION IF NOT EXISTS vector;

COMMENT ON TABLE flyway_schema_history IS
    'Luci baseline migration applied. Walking skeleton — no domain tables yet.';
```

**Effect verifiable from a query** (FR-014):

```sql
SELECT description
FROM   pg_description
WHERE  objoid = 'flyway_schema_history'::regclass AND objsubid = 0;
-- → 'Luci baseline migration applied. Walking skeleton — no domain tables yet.'
```

**Invariants**:

- Applies identically against the local Postgres image (`ghcr.io/tembo-io/pg-pgmq:pg15`, `vector` preinstalled) and against Supabase Postgres (where `CREATE EXTENSION IF NOT EXISTS vector` is the operative form). (FR-014, FR-015.)
- Second run against an already-migrated DB is a no-op via Flyway's checksum/history mechanism (FR-016, SC-007).
- Re-creating the comment on an already-commented `flyway_schema_history` is idempotent (Postgres `COMMENT ON` overwrites).
- Edits outside Flyway (e.g., via Supabase Studio) are forbidden in spirit by the constitution; CI drift detection is downstream of this feature.

**Subsequent migrations** layer on top of this baseline. No new entity introduced here precludes any later domain table; the file is intentionally minimal.

---

## Out-of-scope shapes (not modelled by this feature)

The following appear in `docs/Luci_Tech_Design_v3.md` §5.1 but are explicitly **deferred** by FR-032: `users`, `categories`, `transactions`, `subscriptions`, `idempotency_keys`, `audit_log`, `pdf_ingestion_jobs`, `mercadopago_events`, `llm_usage`, `transaction_aliases`, `parser_feedback`. Each lands with the feature that exercises it; the skeleton MUST NOT introduce them speculatively.
