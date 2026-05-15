<!--
This repo enforces a constitution (.specify/memory/constitution.md) and
Spec Kit workflow. PRs that don't reference the principles they touch or
the spec they fulfil are usually a sign of unscoped work — please push back
and split.
-->

## Summary

<!-- 1-3 sentences. The "why". Diff is the "what". -->

## Spec Kit linkage

- Feature directory: `specs/<NNN-slug>/`
- Spec stage produced this work: <!-- specify | clarify | plan | tasks | implement | analyze | n/a -->

## Constitution check

Tick every box that applies. If a box is ticked, name the principle(s) in the comment.

- [ ] **I — Deterministic Money** — touches money math, schemas with numeric fields, or `Decimal` boundaries
- [ ] **II — LGPD lifecycle** — touches `users`, `audit_log`, deletion worker, PII handling, or retention
- [ ] **III — Contract-first** — touches `openapi.yaml` or generated clients
- [ ] **IV — Test-First & Eval-Gated** — adds tests, changes eval suite, or alters prompts/schemas/validators
- [ ] **V — Idempotency / Audit / Observability** — adds `@PostMapping`, outbound `httpx` POST, webhook handler, or telemetry
- [ ] **VI — Prompts/Schemas/Migrations Are Code** — touches `prompts/`, `schemas.py`, or `db/migration/`
- [ ] **VII — Tier discipline** — modifies a T1 surface (contract, migration, prompt, schema, ADR)

Principles touched: <!-- e.g. "III + VI: openapi.yaml + Flyway baseline" -->

## Tier of the change

- [ ] **T1 — Governance** (contract / migration / prompt / schema / ADR)
- [ ] **T2 — Execution** (controller / service / tool / test)
- [ ] **T3 — Refactor** (wide-context; eval results attached)

## Eval gate

- [ ] Not applicable — change does not touch `prompts/`, `tools.py`, `schemas.py`, `output_validators.py`, or `fewshots/`
- [ ] Required suites attached (`text_intents`, `adversarial`, `prompt_injection`, `zero_math` ≥ thresholds in §IV)
- [ ] PDF eval result attached (required when `schemas.py` or `tools.py` changed)
- [ ] Langfuse PR diff comment posted

## Test plan

<!-- Bullets. What did you run locally? What still needs to be verified after merge? -->

- [ ]
- [ ]

## Deploy ordering

For contract or schema changes only:

- [ ] Spring deploys before Python (additive)
- [ ] Two-release deprecation window planned (destructive)
- [ ] N/A

## Rollback

<!-- One sentence. Default for prompts: revert current.md symlink. Default for code: previous SHA via doctl. -->

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
