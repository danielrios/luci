# Constitution Alignment Checklist: Foundation — Walking Skeleton (Local + Cloud)

**Purpose**: Self-review pass before `/speckit-plan`. Validates that `spec.md` is *written* to be compatible with each of the seven NON-NEGOTIABLE constitutional principles. **This is a unit test for the spec's English — not for any implementation.** Each item asks whether the requirements *as written* are complete, clear, consistent, measurable, and unambiguous with respect to a specific constitutional principle.

**Created**: 2026-05-15
**Feature**: [spec.md](../spec.md)
**Constitution**: [.specify/memory/constitution.md](../../../.specify/memory/constitution.md) v1.0.0
**Audience**: Spec author (pre-`/speckit-plan` self-review)
**Depth**: Standard (22 items)

> Tags: `[Completeness]` `[Clarity]` `[Consistency]` `[Coverage]` `[Measurability]` `[Edge Case]` `[Gap]` `[Ambiguity]` `[Conflict]` `[Assumption]` `[Traceability]`. Each item references a specific spec section or marks the gap.

## Principle I — Deterministic Money, LLM-Free Arithmetic

- [x] CHK001 Are the negative requirements that keep this feature compatible with Principle I (no `BalanceProjector`, no `@agent.tool`, no `Agent.run()`, no LLM call) enumerated *concretely enough* that a reviewer can mechanically detect a violation in a follow-up PR? [Completeness, Spec §FR-032]
- [x] CHK002 Does the spec define what the Flyway baseline migration MAY introduce *and* what it MUST NOT introduce, such that no currency column type (e.g., `DECIMAL(14,2)` / `DECIMAL(18,4)`) sneaks into `V1__baseline.sql` and pre-decides a domain decision? [Clarity, Spec §FR-014]
- [x] CHK003 Are the language-level anti-vibe rules invoked by FR-027 / FR-028 (no `Double`/`Float` for currency, no `print()`) given a *test stimulus* that exercises them at skeleton time — i.e., is the synthetic-violation test in Story 4 explicit about the currency-type rule even though there is no real domain code yet? [Measurability, Spec §FR-027, §FR-028, §US4 Independent Test]

## Principle II — Privacy-by-Default & LGPD Lifecycle

- [x] CHK004 Does the spec state, in normative form, that the Day-0 structured JSON logs (FR-026a) flowing through this skeleton MUST be compatible with the future `PiiRedactor` filter (i.e., logs are structured and field-tagged, not free-text)? [Coverage, Spec §FR-026a, Constitution §II]
- [x] CHK005 Are the LGPD lifecycle invariants (one-tap `/cancel`, 15-day hard-purge, pseudonymized audit retention) explicitly marked **out of scope** for this feature in a single canonical place, so a future feature plan can't read silence as permission? [Gap, Spec §"Out of Scope"]
- [x] CHK006 Does the spec say whether the baseline migration is permitted to create PII-bearing tables (`users`, `transactions`, etc.) at all, or is it strictly limited to infrastructure scaffolding (the `_skeleton_marker` artifact)? [Clarity, Spec §FR-014, Constitution §VI]

## Principle III — Contract-First Service Integration

- [x] CHK007 Is `openapi.yaml`'s required content specified *exhaustively* for this feature — naming exactly which endpoints (`/v1/internal/whoami`, `/health` on both services, `/.well-known/jwks.json`) and which response schemas (Actuator-shape `HealthReport`, JWKS, JWT-claim echo) must be present and which must not? [Completeness, Spec §FR-002]
- [x] CHK008 Is the springdoc-parity invariant (served spec MUST match committed `openapi.yaml`) declared as a *structural prerequisite* now, even though the CI check itself lands later? [Clarity, Spec §FR-002a, Constitution §III]
- [x] CHK009 Is the generated-Python-client requirement specified with enough operational detail — *who* regenerates (which `make` target), *when* (bootstrap / on openapi change), *where* the artifact lands — that a planner cannot under-specify the codegen step? [Completeness, Spec §FR-002b]
- [x] CHK010 Does the spec affirm that the constitutional ban on hand-written `httpx` to Spring is enforced *with no skeleton exemption*, and is that consistent across FR-002b, the lint rule references (FR-027), and the Out-of-Scope section? [Consistency, Spec §FR-002b, §FR-027]
- [x] CHK011 Is the deploy-ordering invariant (Spring first, Python after, on additive contract changes) referenced or deferred *explicitly*, so the prod parity claim in Story 3 cannot be misread as "deploy in any order"? [Gap, Spec §US3]

## Principle IV — Test-First & Eval-Gated Delivery

- [x] CHK012 Are the "Independent Test" sentences under each user story written precisely enough that a failing test can be authored from them alone (concrete inputs, concrete expected outputs, concrete invocation), without consulting the plan? [Measurability, Spec §US1-US4 Independent Test]
- [x] CHK013 Is the eval-suite scaffolding decision (no datasets land in this feature; layout only — Assumption A-9) consistent with FR-033/FR-034 (no prompts, no schemas) so that there is no incentive to ship a half-built eval gate? [Consistency, Spec §A-9, §FR-033, §FR-034]
- [x] CHK014 Does the spec specify that the Story-2 auth-seam test (Testcontainers + `respx`-style integration) is the *first failing test the skeleton must support*, rather than a deferred concern? [Completeness, Spec §US2, Constitution §IV]

## Principle V — Idempotency, Audit & Observability by Construction

- [x] CHK015 Is the question of whether `POST /v1/internal/whoami` requires an `Idempotency-Key` header resolved either way in the spec — required, waived, or explicitly deferred — given that `whoami` is the only POST introduced by this feature and Constitution §V demands "every state-mutating POST"? [Ambiguity, Spec §FR-020, Constitution §V]
- [x] CHK016 Are the day-0 observability scaffolds (structured JSON logs + Prometheus `/metrics`) specified with measurable acceptance criteria (log shape fields enumerated, minimum metrics listed, scrape format named) rather than the vague "observable from day 1" the constitution alone provides? [Measurability, Spec §FR-026a, §FR-026b]
- [x] CHK017 Is the boundary between "scaffolded in skeleton" and "wired to real backends later" stated unambiguously — i.e., does the spec say which sinks (Prometheus server, OTel collector, Langfuse) are *deferred* and which surfaces (`/metrics`, JSON logger, OTel SDK init) are decided right now? [Clarity, Spec §FR-026d, §"Out of Scope"]
- [x] CHK018 Does the spec specify the `AuditLogAspect` and Langfuse-wrapping requirements are deliberately deferred (since there is no `@agent.tool` yet) rather than silently omitted, so Principle V's audit clause cannot be read as already satisfied? [Gap, Constitution §V]

## Principle VI — Prompts, Schemas & Migrations Are Code

- [x] CHK019 Are the three negative scopes — no FROZEN prompt (FR-033), no LLM output schema (FR-034), Flyway-only schema ownership (FR-014) — internally consistent with each other and with the directory-structure requirements (empty `agent/orchestrator/prompts/` scaffold permitted, `schemas.py` reserved)? [Consistency, Spec §FR-014, §FR-033, §FR-034]
- [x] CHK020 Is the Flyway baseline migration specified to be **additive only**, with an explicit prohibition on `DROP COLUMN` or type-narrowing in `V1__baseline.sql`, matching the constitutional two-release-deprecation rule? [Completeness, Spec §FR-014, §FR-016, Constitution §VI]
- [x] CHK021 Is the question of CI-detected drift between Flyway-owned schema and any out-of-band edits (e.g., via Supabase Studio) explicitly *deferred* with a named follow-on, rather than left ambiguous? [Clarity, Spec §FR-015]

## Principle VII — Tiered, Tool-Agnostic AI-Assisted Development

- [x] CHK022 Does the spec correctly identify which artifacts it introduces are **T1 (Governance-owned)** — `openapi.yaml`, the Flyway baseline, the (empty) `agent/orchestrator/prompts/` scaffold, and any ADR this feature requires — versus T2 execution artifacts (controllers, services, the lint configs), so that a `/speckit-plan` cannot silently let T2 redefine T1? [Traceability, Constitution §VII]

## Notes

- Check items off as completed: `[x]`.
- A `[Gap]` tag means "the spec is currently silent on this; decide whether silence is intentional (mark out-of-scope) or unintentional (add a requirement)."
- An `[Ambiguity]` tag means "the spec says something on this, but a reader could land on two different implementations from the same sentence."
- A `[Conflict]` tag means "two parts of the spec contradict each other." None are pre-flagged in this draft; surface any you find here.
- If an item resolves to "intentionally deferred", add a one-line entry to `## Out of Scope` in `spec.md` so the deferral is itself traceable.
- This checklist is **read-only with respect to the constitution.** If an item exposes that the constitution itself is wrong or stale, that is a `/speckit-constitution` amendment, not a spec edit.
