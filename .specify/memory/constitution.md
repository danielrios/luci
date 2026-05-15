 <!--
================================================================================
SYNC IMPACT REPORT
================================================================================
Version change: (template, unratified) -> 1.0.0
Bump rationale: Initial ratification of the Luci constitution. No prior versioned
content existed (file held template placeholders only). Per semver, the first
adopted constitution is published as 1.0.0.

Principles defined (new):
  I.   Deterministic Money, LLM-Free Arithmetic (NON-NEGOTIABLE)
  II.  Privacy-by-Default & LGPD Lifecycle (NON-NEGOTIABLE)
  III. Contract-First Service Integration
  IV.  Test-First & Eval-Gated Delivery (NON-NEGOTIABLE)
  V.   Idempotency, Audit & Observability by Construction
  VI.  Prompts, Schemas & Migrations Are Code
  VII. Tiered, Tool-Agnostic AI-Assisted Development

Added sections:
  - Core Principles (I-VII)
  - Architectural Constraints & Quality Bars (replaces SECTION_2)
  - Development Workflow & Quality Gates (replaces SECTION_3)
  - Governance

Removed sections:
  - None (template placeholders replaced wholesale)

Templates requiring updates:
  - .specify/templates/plan-template.md ............... ⚠ pending
    (Constitution Check section currently empty placeholder — should be
    populated with the seven gates derived from principles I-VII when
    /speckit-plan is exercised on the first feature)
  - .specify/templates/spec-template.md ............... ✅ no change required
    (spec authoring surface is technology-agnostic; constraints belong in
    plan-template Constitution Check, not in spec)
  - .specify/templates/tasks-template.md .............. ⚠ pending review
    (verify task categories cover: contract tasks, migration tasks,
    eval-suite tasks, observability tasks, LGPD-compliance tasks)
  - .specify/templates/checklist-template.md .......... ⚠ pending review

Runtime guidance docs:
  - docs/Luci_Tech_Design_v3.md ....................... ✅ source of truth
  - docs/PRD_Luci_v2.md ............................... ✅ product source
  - CLAUDE.md ......................................... ⚠ may reference this
    constitution explicitly in a follow-up commit

Deferred / TODOs:
  - None. RATIFICATION_DATE set to 2026-05-15 (date of TDD v3.1 approval and
    project adoption per docs/Luci_Tech_Design_v3.md header).
================================================================================
-->

# Luci Constitution

This document codifies the non-negotiable invariants, architectural principles,
and quality bars for the Luci project (consumer agent, product brand "Lúcido").
It is derived from `docs/Luci_Tech_Design_v3.md` (TDD 3.1, Build-Ready) and
`docs/PRD_Luci_v2.md`. When any other artifact (spec, plan, code, prompt, ADR)
conflicts with this constitution, this constitution wins until amended.

## Core Principles

### I. Deterministic Money, LLM-Free Arithmetic (NON-NEGOTIABLE)

All financial computation — balances, projections, simulations, category
aggregates, dunning math, subscription state — MUST be executed by the Spring
Boot Finance API against Postgres. The LLM MUST NOT perform arithmetic on money
and MUST NOT be given output schema slots that would let it emit a computed
amount.

Concretely:

- Currency columns MUST be `DECIMAL(14,2)` for transactions and
  `DECIMAL(18,4)` for investment positions. `float`/`Float`/`Double`/`Number`
  for currency is FORBIDDEN and CI MUST block it (Python lint rule; Spotbugs
  rule on JVM).
- LLM output schemas (`Transacao`, `SimulationResult`, `DiagnosticReport`,
  etc.) MUST NOT contain `balance`, `total`, `projection`, or any computed
  numeric field. Amounts returned to the user MUST originate from a tool call
  with provenance (`source: "spring", computed_at: ...`) and be emitted by the
  agent as opaque strings.
- The `zero_math.jsonl` eval (100 arithmetic-tempting prompts) MUST pass at
  100% tool-routing on every PR. Failure MUST block merge.

**Rationale:** This is PRD principle P2 and TDD §1 invariant #1. The entire
orchestrator-worker split exists to enforce it. Violating this principle would
expose users to LLM hallucination in a domain (personal finance) where wrong
arithmetic is unrecoverable trust damage.

### II. Privacy-by-Default & LGPD Lifecycle (NON-NEGOTIABLE)

Luci serves Brazilian users and operates under LGPD. The cancellation and
deletion pipeline is a product invariant, not an ops concern.

- `/cancel` MUST execute in a single user action and MUST be reachable from
  any chat state. No retention dark patterns.
- Soft-delete (`users.deleted_at`) MUST occur immediately on cancellation.
- Hard-purge MUST execute no later than 15 days after soft-delete via the
  `DeletionJobScheduler`. The DB-level `CHECK (purged_at >= deleted_at +
  INTERVAL '15 days')` constraint MUST remain. A late purge is a paging-level
  legal incident.
- Audit log entries MUST be pseudonymized at purge time (`sha256(user_id ||
  pepper)`) and retained for 5 years per LGPD Art. 16, V. Raw `user_id` MUST
  NOT survive the purge anywhere outside this hash.
- PII (transaction descriptions, audio, PDF originals, LLM completions) MUST
  flow through the `PiiRedactor` log filter before stdout. PDF originals MUST
  be purged from object storage within 24 hours of ingestion completion.
- Vertex AI MUST be used under ZDR (Zero Data Retention) enrollment in the
  São Paulo region. Self-hosted LLMs and non-ZDR providers are FORBIDDEN for
  user data.

**Rationale:** This is PRD principle P3 and TDD §9 lifecycle. Friction on
cancellation and any whisper of training-on-user-data are existential brand
risks in the Brazilian fintech market and direct regulatory exposure.

### III. Contract-First Service Integration

Inter-service communication between the Python Agent and Spring Boot Finance
API MUST be governed by a single OpenAPI 3.1 contract (`openapi.yaml` at the
monorepo root). The contract is the source of truth; controllers and clients
conform to it, not the other way around.

- Spring Boot MUST generate its served spec from controllers
  (springdoc-openapi). CI MUST diff that generated spec against the committed
  `openapi.yaml` and fail any drift.
- The Python `httpx` client MUST be generated from `openapi.yaml` via
  `openapi-python-client`. Hand-written `httpx` calls to Spring are
  FORBIDDEN and MUST be blocked by a ruff custom rule.
- Any PR touching both `agent/` and `finance-api/` without touching
  `openapi.yaml` MUST be rejected by CI as a contract-drift signal.
- Any PR touching `openapi.yaml` MUST also touch at least one generated
  client artifact, MUST regenerate both ends, and MUST follow the deploy
  ordering invariant: Spring (server) deploys before Python (client) on
  additive contract changes; destructive changes (removing fields/endpoints)
  require a two-release deprecation window.

**Rationale:** The orchestrator-worker split is only as strong as the seam
between them. Drift between LLM-side expectations and deterministic-side
reality reintroduces the very class of bug Principle I exists to prevent.

### IV. Test-First & Eval-Gated Delivery (NON-NEGOTIABLE)

Every change ships with tests written first, and LLM-touching changes ship
with eval results.

- TDD is mandatory across both services. Implementation MUST be preceded by
  a failing test: Testcontainers-backed integration tests for Spring, and
  `respx` + recorded Gemini cassettes for Python. The TDD gate is enforced by
  the mandatory `speckit.superb.tdd` hook before implementation.
- No real Gemini, Mercado Pago, or Telegram calls in the PR pipeline.
  Recorded cassettes (Python) and MockServer expectations (Spring) are the
  only allowed external surfaces. New cassettes MUST be recorded explicitly
  with `LUCI_LLM_MODE=live` and reviewed in PR.
- The eval gate (`luci-evals` submodule) MUST run on every PR that touches
  `agent/orchestrator/prompts/`, `tools.py`, `schemas.py`,
  `output_validators.py`, or `fewshots/`. Required suites and thresholds:
  - `text_intents.jsonl` ≥ 95% F1
  - `adversarial.jsonl` ≥ 80% F1
  - `prompt_injection.jsonl` 0 successful exploits
  - `zero_math.jsonl` 100% tool-routed
  - PDF eval ≥ 90% F1 (nightly; required on `schemas.py` / `tools.py` PRs)
- Skip flags on the eval gate are FORBIDDEN.
- Completion is enforced by the mandatory `speckit.superb.verify` hook after
  implementation; false-completion claims are blocked there.

**Rationale:** Traditional unit tests do not catch prompt regressions, schema
drift, or LLM behaviour shift. Without an eval gate, the only signal of a
regression is user pain. Without TDD, the orchestrator-worker contract
decays into untestable glue.

### V. Idempotency, Audit & Observability by Construction

Every state-mutating operation MUST be safe to replay, MUST be auditable, and
MUST be observable from the first commit — never bolted on later.

- Every Spring POST mutating state MUST require an `Idempotency-Key` header
  (UUID). The `idempotency_keys` table row MUST be written in the same
  transaction as the state change. CI lint MUST verify every `@PostMapping`
  is `@Idempotent` (or explicitly waived in code review).
- Every outbound `httpx` POST from the Python agent to Spring MUST inject an
  `Idempotency-Key`. Lint MUST enforce this.
- Inbound webhooks MUST be cryptographically verified before any side effect:
  Telegram via `X-Telegram-Bot-Api-Secret-Token`, Mercado Pago via
  HMAC-SHA256, internal callbacks via M2M JWT + HMAC.
- Every `@agent.tool` call MUST be audit-logged via the `AuditLogAspect`
  (`intent`, `tool_name`, `input_hash`, `output_hash`, `latency_ms`,
  `trace_id`, pseudonymized `user_ref`).
- Three pillars MUST be live from day 1: structured JSON logs (`structlog` /
  `logstash-logback-encoder`), Prometheus metrics, OpenTelemetry traces.
  Langfuse MUST wrap every `Agent.run()`.
- Cost telemetry: every LLM call MUST write to `llm_usage`. A per-user daily
  hard cap (R$ 5.00) MUST kill-switch further LLM calls. Cohort p99 daily cost
  MUST alert when it crosses R$ 0.10.

**Rationale:** Retrofitting idempotency after a double-charge incident is
expensive; retrofitting audit after an ANPD inquiry is impossible.
Observability gaps in an LLM system are particularly dangerous because the
failure mode is silent quality regression, not a stack trace.

### VI. Prompts, Schemas & Migrations Are Code

Prompts, output schemas, and database migrations are first-class governed
artifacts on the same review surface as application code — and held to a
stricter discipline because their blast radius is wider.

- System prompts MUST live in `agent/orchestrator/prompts/system_v{N}.md`
  versioned in Git. The active version is selected via the `current.md`
  symlink. Inlining prompts in business logic is FORBIDDEN.
- Once a prompt ships, `system_v{N}.md` MUST be marked `# FROZEN: shipped
  <YYYY-MM-DD>` and becomes append-only. Changes MUST create
  `system_v{N+1}.md`. Editing a frozen prompt MUST be blocked by CI.
- String concatenation of user input into prompt strings is FORBIDDEN.
  User-supplied text (chat, PDF, OCR) MUST be wrapped in explicit delimiter
  templates (e.g., `<user_supplied_document>...</user_supplied_document>`).
- The agent's tool surface MUST be static at boot. Dynamic tool registration
  is FORBIDDEN.
- Database schema is owned by Flyway in `finance-api/src/main/resources/
  db/migration/`. Edits via Supabase Studio are FORBIDDEN and CI MUST detect
  drift. Migrations MUST be additive on a single deploy; `DROP COLUMN` and
  type-narrowing require a two-release deprecation window.
- Rollback for prompts is git-native (repoint `current.md` symlink + redeploy);
  RTO target < 5 minutes. The rollback runbook in `docs/runbooks/
  prompt-rollback.md` MUST stay current.

**Rationale:** Prompts ship behavior to production with no compiler. Migrations
ship destructive operations with no undo. Treating either as configuration
rather than code is the path to silent regressions and 3am rollbacks.

### VII. Tiered, Tool-Agnostic AI-Assisted Development

Development discipline is permanent; AI tool bindings are reversible. The
three-tier surface model MUST be respected regardless of which assistant is
in use today.

- **T1 — Governance** owns: `openapi.yaml`, Flyway migrations,
  `agent/orchestrator/prompts/`, `agent/orchestrator/schemas.py`, ADRs in
  `docs/adr/`. T1 artifacts MUST NOT be modified outside a Governance task.
- **T2 — Execution** owns: controllers, services, agent tools, unit and
  integration tests. T2 MUST conform to T1 artifacts — it MUST NOT redefine
  contracts, schemas, or migrations to suit an implementation shortcut.
- **T3 — Refactoring & Tuning** is read-only across the repo for analysis
  and produces patches that go through T1+T2 review. T3 MUST NOT commit
  directly to `main`.
- Tool swaps **within** a tier are silent; cross-tier reassignment (e.g.,
  letting an IDE assistant edit migrations) requires an ADR.
- Wide-context refactors MUST land via PR with eval-suite results attached.

**Rationale:** AI assistants drift across surfaces under loose prompts and
hallucinate contracts that don't exist. The tier discipline is what keeps the
contract-first principle (III) and the prompts-as-code principle (VI)
enforceable in practice.

## Architectural Constraints & Quality Bars

These constraints are derived from TDD v3.1 and bind every feature plan
unless explicitly amended via the Governance procedure below.

**Architecture pattern:** Event-driven Orchestrator–Worker.

- **Python Agent (Pydantic AI + Gemini 2.5 Flash Lite via Vertex AI ZDR, São
  Paulo)** owns Telegram I/O, intent parsing, LLM orchestration, PDF/audio
  multimodal extraction, and Matplotlib chart rendering.
- **Spring Boot Finance API (JDK 21, Kotlin/Java)** owns all deterministic
  financial computation, Mercado Pago integration, audit log writes,
  idempotency, the subscription state machine, and the LGPD deletion worker.
- **Single source of truth:** Supabase Postgres 15 + pgvector + pgmq.
- **Cache & rate-limit counters:** Redis (Upstash). Spring is authoritative
  for shared counters.

**Tenant isolation (MVP):** Application-layer in Spring repositories;
mandatory `userId` filter on every query, enforced by AOP. Postgres RLS is
deferred to Phase 2 (read-only Next.js dashboard via Supabase Auth).

**Service-to-service auth:** M2M RS256 JWT (15-minute TTL, quarterly key
rotation, JWKS at `/.well-known/jwks.json`) + HMAC on the most sensitive
endpoints. mTLS is deferred to Phase 4 (Cloud Run + VPC). User-JWT
impersonation between services is FORBIDDEN.

**Performance & cost SLOs:**

- p95 ≤ 3,000 ms text-intent latency at 50 RPS sustained.
- Per-user monthly COGS ≤ R$ 1.47; gross margin ≥ 80%.
- LLM fallback rate < 2%; Spring 5xx < 1%.
- RTO 4h, RPO 15 minutes (Supabase PITR).

**Resilience baseline:** Resilience4j circuit breakers on every external
call site with documented thresholds (TDD §10.1). Bounded async executors
(max 20 concurrent) plus Redis per-user locks on projection recomputation.

**Anti-vibe rules (CI-enforced, non-exhaustive):**

- Python: `mypy --strict`; no `Any` in function signatures; no `# type:
  ignore` without justification comment; `Decimal` mandatory for currency;
  no `print()` — only `logger.*`.
- JVM: no `Double`/`Float` for currency (Spotbugs); no raw stack trace
  returns (PMD); no `@SuppressWarnings("all")`.
- Both: no SQL string concatenation; all state-mutating writes idempotent;
  all POSTs require `Idempotency-Key`; no secrets in repo, logs, or
  env-debug endpoints (gitleaks pre-commit + CI).

**Out of scope for MVP (will be rejected unless an amendment lands):**

- Open Finance aggregation (Pluggy/Belvo/Celcoin)
- gRPC, GraphQL, or service mesh
- Native mobile apps
- Web dashboard (read-only dashboard belongs in Phase 2)
- MCP server exposure
- Self-hosted LLMs

## Development Workflow & Quality Gates

The pipeline from spec to production is gated. Each gate is mandatory; skipping
a gate requires either an amendment to this constitution or a one-off waiver
documented in the PR description and approved by a Governance reviewer.

**Pre-commit:** ruff + ktlint + mypy strict + gitleaks + Spotbugs custom rules.

**PR gate (must pass before merge to `main`):**

1. Unit tests (both services).
2. Integration tests under Testcontainers (Postgres 15 + pgmq + pgvector,
   Redis 7, MockServer for Mercado Pago).
3. OpenAPI spec drift check (springdoc-generated vs. committed
   `openapi.yaml`).
4. Generated-client freshness check (Python `httpx` client regenerated and
   diffed against committed code).
5. Eval suites that the change requires per Principle IV.
6. Anti-pattern guards: no `agent/`+`finance-api/` change without
   `openapi.yaml`; no `openapi.yaml` change without a generated-client
   touch; no edit to a `# FROZEN` prompt; no hand-written `httpx` to Spring;
   no `DROP COLUMN` without deprecation window.
7. Flyway shadow validation against ephemeral Postgres.

**Nightly:** PDF eval suite (60 min) on the latest `main`.

**Deploy:**

- Blue/green via DigitalOcean App Platform with auto-rollback on health-check
  failure.
- Deploy ordering on contract or schema changes: Spring first, Python after.
  Enforced by GitHub Actions `needs:` chain.
- Synthetic post-deploy probe: Telegram message round-trip < 3 s.

**Rollback RTOs:** application code < 2 min; prompt regression < 5 min;
contract regression < 10 min. Database destructive changes are forbidden in
MVP — forward-fix migrations only.

**Feature flags:** Unleash OSS self-hosted. Kill-switches for
`proactive_nudges_enabled`, `simulation_free_teaser_enabled`,
`gpt_fallback_enabled`. Untested prompt changes shipped behind a flag without
eval coverage at the flag-on path are FORBIDDEN.

**Spec-driven workflow:** Features begin via `/speckit-specify` →
`/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` →
`/speckit-implement`. The mandatory hooks `speckit.git.feature` (before
specify), `speckit.superb.tdd` (before implement), and `speckit.superb.verify`
(after implement) MUST remain enabled.

## Governance

This constitution supersedes all other practices for the Luci project.
Conflicts between this document and a spec, plan, PR, ADR, or runbook are
resolved in favor of this constitution until an amendment is ratified.

**Amendment procedure:**

1. Open an ADR in `docs/adr/` describing the proposed change, the principle(s)
   affected, the migration plan, and the rollback strategy.
2. Update this file via `/speckit-constitution`. The Sync Impact Report at
   the top of the file MUST list the version bump, modified principles,
   added/removed sections, and any templates flagged for follow-up.
3. Propagate consequent changes across `.specify/templates/*.md` and any
   runtime guidance docs (`CLAUDE.md`, `docs/runbooks/*`).
4. Bump the version per the rules below.
5. Merge via PR with at least one Governance reviewer approval.

**Versioning policy (semantic):**

- **MAJOR**: Removal or backward-incompatible redefinition of a principle, or
  a structural change that invalidates existing plans (e.g., abandoning the
  orchestrator-worker split, dropping LGPD invariants).
- **MINOR**: New principle, new mandatory gate, or materially expanded
  guidance on an existing principle.
- **PATCH**: Clarifications, wording, typo fixes, non-semantic refinements
  that do not change which behaviors are required or forbidden.

**Compliance review expectations:**

- Every PR description MUST cite which principles the change touches when it
  modifies T1 surfaces (contracts, prompts, schemas, migrations).
- `/speckit-plan` MUST execute a Constitution Check against principles I–VII
  before Phase 0 research and re-check after Phase 1 design. Violations land
  in the plan's Complexity Tracking table with explicit justification.
- A quarterly self-audit (Q1, Q2, Q3, Q4) MUST verify: (a) CI lint rules
  still enforce every "MUST"/"FORBIDDEN" clause in this constitution, (b) the
  rollback runbooks still match production reality, (c) ratification dates
  on frozen prompts match the `# FROZEN: shipped <date>` annotation.

**Runtime guidance:** Day-to-day implementation guidance lives in
`docs/Luci_Tech_Design_v3.md` (technical) and `docs/PRD_Luci_v2.md`
(product). When TDD and this constitution diverge, this constitution wins
and the TDD MUST be updated within one sprint.

**Version**: 1.0.0 | **Ratified**: 2026-05-15 | **Last Amended**: 2026-05-15