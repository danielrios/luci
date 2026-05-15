# Product Requirements Document — Luci (Lúcido)

| Field | Value |
|---|---|
| **Product** | Luci (consumer-facing agent) / Lúcido (product brand) |
| **Version** | 2.0 (MVP-Ready) |
| **Status** | Final — Approved for Tech Design |
| **Owner** | Daniel (Founder / Tech Lead) |
| **Last Updated** | 2026-05-15 |
| **Launch Window** | 8–12 weeks from Tech Design sign-off |
| **Target GA** | Q3 2026 |
| **Linked Docs** | `Brainstorm — Agente IA.md`, `luci_deep_research.md`, `Brazilian_AI_Finance_Agent_Research.md` |

---

## 1. Executive Summary

### 1.1 Product Vision

Transform personal finance management in Brazil from a manual, reactive chore into a conversational, proactive, zero-friction interaction. Luci ingests transactions through natural language, surfaces contextual insights, and simulates the impact of purchasing decisions in real time — entirely inside the user's existing messenger.

### 1.2 Strategic Bet

The Brazilian PF-AI market has no dominant player. The leader — Pierre (CloudWalk, 165k users) — owns the conversational layer but **rejected visualization on principle** and is hemorrhaging trust due to balance miscalculations and friction-laden cancellation. Luci wins by combining three layers no competitor unifies:

```
Deep Brazilian financial specialization
        +
Conversational agent with persistent memory
        +
On-demand contextual visualization (charts in-chat)
```

### 1.3 North Star Metric

**Weekly Active Conversations per User (WACU)** — measured as distinct intents executed per active user per 7-day window.
Target by Day 90: **≥ 4 WACU** (vs. industry baseline of ~2 for WhatsApp finance bots).

### 1.4 Success Criteria — First 90 Days

| Tier | Metric | Target | Rationale |
|---|---|---|---|
| **North Star** | WACU | ≥ 4 | Habit-formation proxy |
| **Retention** | D30 retention | ≥ 40% | PMF threshold for B2C SaaS |
| **Retention** | M3 retention | ≥ 25% | Subscription viability |
| **Activation** | Time-to-first-insight | ≤ 3 messages | UX promise: zero onboarding friction |
| **Monetization** | Free→Pro conversion | ≥ 4% | Required to amortize CAC at R$9.90 ARPU |
| **Monetization** | Monthly churn | ≤ 8% | Below Meu Assessor (~12% inferred) |
| **Unit Economics** | Gross margin | ≥ 80% | Beats the 75% planning floor |
| **AI Quality** | Parsing accuracy (BR PT-BR transactions) | ≥ 95% F1 | Trust threshold — below this, user abandons |
| **Performance** | p95 perceived latency (text intent) | ≤ 3,000 ms | Conversational pact |

---

## 2. Problem Statement

### 2.1 Problem Definition

Personal finance tracking in Brazil today fails at two extremes:

1. **Discipline-heavy tools** (spreadsheets, Mobills, Organizze) demand sustained manual entry that 78% of users abandon within 60 days.
2. **AI-aggregator tools** (Pierre, Financinha, Meu Assessor) suffer from broken bank syncs, hallucinated math, and dark-pattern offboarding — actively eroding trust.

Neither delivers **decision support at the moment of purchase** — the only moment where insight changes behavior.

### 2.2 Impact Analysis

| Dimension | Quantified Pain | Luci's Wedge |
|---|---|---|
| **Cognitive load** | "Can I afford this?" answered in 4–7 mins of app navigation today | Deterministic answer in ≤ 3 s, in-chat |
| **Trust erosion** | Pierre's Reclame Aqui flags balance miscalculations as #1 complaint | Spring Boot as single source of truth; LLM forbidden from arithmetic |
| **Cancellation friction** | Meu Assessor scores 6.4/10 on Reclame Aqui — cancellation friction is #1 reason cited | `/cancel` 1-tap; deletion within 15 days; LGPD-compliant |
| **Cost-of-AI ceiling** | Competitors using GPT-4o-mini face ~R$0.04/user/mo LLM COGS | Gemini 2.5 Flash Lite multi-model strategy: ~R$0.025/user/mo |

---

## 3. Target Audience

### 3.1 Primary Persona — "Tech-Aware Busy Professional"

| Attribute | Profile |
|---|---|
| **Age** | 24–35 |
| **Vertical** | Tech, design, corporate finance, product |
| **Income** | R$ 8k–25k/mo |
| **Psychographic** | Hates dark patterns; values clean UX or absence of UX; high digital literacy |
| **Devices** | Mobile-first; Telegram heavy user (or willing to adopt) |
| **Jobs to be Done — Functional** | Log expenses without breaking flow; project end-of-month balance |
| **Jobs to be Done — Emotional** | Feel financially in control without auditing spreadsheets |
| **Jobs to be Done — Social** | Quietly competent with money — not "the one who never knows their balance" |

### 3.2 Secondary Persona — "Disillusioned Refugee"

Former Meu Assessor / Financinha / Pierre user actively looking for an alternative. **High intent, lower CAC**, fastest conversion path. PRD-level commitment: dedicated landing page comparing offboarding flows; cancellation as marketing.

### 3.3 Anti-Persona — Explicitly NOT for

- Users seeking holistic life-assistance (generalist bots) — Meu Assessor's territory.
- Users wanting investment advice / robo-advisor — regulatory bar too high for MVP.
- Users requiring offline-first mobile-native experience — Telegram dependency makes this a non-goal.

---

## 4. Product Principles

| # | Principle | Implication |
|---|---|---|
| P1 | **The agent is the product; the dashboard is the complement** | Engineering investment ratio ≥ 70:30 agent:dashboard |
| P2 | **Money math never touches the LLM** | All arithmetic routed to Spring Boot as deterministic tool calls |
| P3 | **Cancellation is a feature, not friction** | `/cancel` reaches deletion within 15 days, no friction screens |
| P4 | **First insight in three messages** | Onboarding is a conversation, not a form |
| P5 | **Specialization > breadth** | Refuse scope creep into agenda/tasks/notes/files |
| P6 | **Visualization is contextual, not decorative** | Charts only when they reduce decision time |

---

## 5. User Stories & Core Loops

### 5.1 Story Hierarchy

**Epic:** Conversational Financial Tracking & Decision Making

| ID | Story | Tier | Persona |
|---|---|---|---|
| **S1** | Frictionless transaction logging (text + audio) | P0 | Primary |
| **S2** | "How am I doing?" diagnostic | P0 | Primary |
| **S3** | Decision simulation with visual projection (anchor feature) | P0 | Primary (Pro-gated w/ Free teaser) |
| **S4** | PDF statement ingestion (credit card, checking) | P0 | Both |
| **S5** | Frictionless onboarding (no form, no setup) | P0 | Both |
| **S6** | Frictionless offboarding (`/cancel`) | P0 | Secondary (acquisition lever) |
| **S7** | Pro upgrade via Pix Automático | P0 | Primary |
| **S8** | Proactive nudge on projected negative balance | P1 (Pro only) | Primary |
| **S9** | Manual investment tracking (ticker + qty + avg price) | P1 | Primary |
| **S10** | Annual patrimonial narrative (LLM-generated) | P2 (Pro only) | Primary |

### 5.2 Detailed Acceptance Criteria

#### S1 — Frictionless Logging

> "As a user, I want to log an expense while still at the checkout counter, without opening a separate app or filling a form, so I don't lose the tracking habit."

| Criterion | Specification |
|---|---|
| Channels | Text and audio (speech-to-text via Gemini multimodal) |
| Response time | p95 ≤ 3,000 ms perceived in Telegram |
| Output | Value confirmation + inferred category + updated daily/monthly impact |
| Edge — ambiguous value | If parser confidence < 0.85, agent asks 1 clarifying question (never silently guesses) |
| Edge — currency mismatch | If non-BRL value detected without explicit currency, default BRL; flag in audit log |
| Edge — investment deposit | If parser classifies "aporte" / "investimento" as expense, output validator forces reclassification to "patrimonial movement" |
| Failure mode | If Spring Boot times out (> 8 s), agent returns graceful message and queues the transaction for retry |

#### S2 — "Am I OK?" Diagnostic

> "As a user, I want to ask how my month is going and get a natural-language answer with temporal context."

| Criterion | Specification |
|---|---|
| Trigger phrases | "como tô esse mês?", "tô no azul?", open-ended balance queries |
| Response composition | Current balance + end-of-month projection + at least 1 anomaly insight (variance ≥ 25% vs. 3-month rolling average) |
| Math source | All aggregates from Spring Boot `GET /v1/users/{id}/snapshot` — LLM never recomputes |
| Edge — insufficient history | If < 30 days of data, agent states this explicitly and offers PDF import to bootstrap |

#### S3 — Decision Simulation (Anchor Feature)

> "As a user, I want to simulate an expense before making it and get a visual impact projection."

| Criterion | Specification |
|---|---|
| Trigger | "posso parcelar X em N vezes?", "dá pra comprar Y?", scenario phrasing |
| Output | (a) Diagnostic verdict (approve / caution / reject), (b) Static PNG line chart: baseline projection vs. simulated scenario |
| Chart generation | Backend async worker (Matplotlib); chart byte-array sent via Telegram `sendPhoto` |
| Free-tier gating | 1 simulation/month for Free users (teaser); unlimited Pro |
| Edge — missing data | Required: ≥ 14 days of history. If unmet, agent declines simulation and proposes alternatives |

#### S5 — Frictionless Onboarding

| Criterion | Specification |
|---|---|
| Setup steps | Zero. No form, no category list, no goal-setting wizard |
| Activation path | First message → parser → tool call → response with first insight |
| Module activation | Implicit, intent-driven: "meus investimentos" activates patrimony module; "quanto gasto" activates statement module |
| Time-to-first-insight | ≤ 3 messages exchanged |

#### S6 — Frictionless Offboarding

| Criterion | Specification |
|---|---|
| Command | `/cancel` in Telegram |
| Confirmation | One in-chat confirmation prompt; no retention attempts, no surveys |
| Billing | Subscription mandate revoked immediately via Mercado Pago API |
| Data deletion | All user data purged within **15 days** (LGPD Article 18, V); confirmation email sent |
| Re-onboarding | Account fully reusable if user returns; data restart from zero |

---

## 6. Functional Requirements

### 6.1 MVP Scope — Must Have (P0)

| # | Capability | Owner | Dependencies |
|---|---|---|---|
| F1 | Telegram bot interface (webhook-based) | Python Agent | Telegram Bot API |
| F2 | Conversational parser (text + audio) → structured JSON | Python Agent | Gemini 2.5 Flash Lite |
| F3 | Multimodal PDF parsing (statements, credit-card bills) | Python Agent | Gemini 2.5 Flash (1M context) |
| F4 | On-demand chart generation (line, comparison) | Spring Boot | Matplotlib worker |
| F5 | Balance projection engine ("day X will hit zero") | Spring Boot | PostgreSQL |
| F6 | Budget simulation engine | Spring Boot | Projection engine |
| F7 | Pix Automático subscription billing | Spring Boot | Mercado Pago API |
| F8 | `/cancel` flow with deletion job | Spring Boot | LGPD compliance worker |
| F9 | Rate-limited Free tier enforcement | Spring Boot | Redis counter |
| F10 | Auditability log (user_id, intent, hashes) | Both services | Structured logger |

### 6.2 Should Have (P1 — post-MVP, ≤ 60 days after GA)

| # | Capability | Trigger to Build |
|---|---|---|
| F11 | Proactive nudges on projected negative balance | Pro user volume ≥ 100 |
| F12 | Manual investment tracking + brapi.dev quotes | Free retention ≥ 40% |
| F13 | Annual patrimonial narrative | Pro tier validated |
| F14 | Web dashboard (read-only consolidation) | Pro complaints about chat-only mode > 10% |

### 6.3 Out of Scope — Won't Have (Explicit)

| # | Capability | Rationale | Revisit Trigger |
|---|---|---|---|
| O1 | Open Finance integration (Pluggy/Belvo/Celcoin) | R$2,500/mo minimum destroys margin under ~1,000 Pro users | ≥ 1,000 Pro paying users |
| O2 | Investment/credit recommendations | Triggers "high-risk AI" classification under PL 2338/2023 | Regulatory landscape stabilizes (2028+) |
| O3 | Native iOS/Android app | Telegram covers messaging UX; building app duplicates effort | M6 if Telegram-only friction exceeds 15% in surveys |
| O4 | WhatsApp channel | Meta API costs + Meta data sovereignty concerns | Pro user demand signal ≥ 30% |
| O5 | Bank-direct screen scraping | Banco Central regulatory risk; LGPD exposure | Never (architectural decision) |
| O6 | Generalist features (agenda, tasks, notes) | Anti-Meu-Assessor positioning | Never |
| O7 | MCP server exposure | Premature; no 3rd-party consumers in MVP | ≥ 3 external integrators request it |

---

## 7. Pricing & Monetization

### 7.1 Pricing Architecture

| Tier | Price | Margin Target | Strategic Role |
|---|---|---|---|
| **Free** | R$ 0 | N/A (CAC channel) | Acquisition funnel; ~R$0.28/user/mo cost |
| **Pro Monthly** | R$ 9.90/mo | ≥ 80% gross | Primary revenue; below competitors (R$16.90 Financinha) |
| **Pro Annual** | R$ 99.90/year | ≥ 80% gross | LTV capture; ~16% effective discount |

### 7.2 Feature Gating

| Capability | Free | Pro |
|---|:---:|:---:|
| Telegram bot interactions | 50 msgs/day | Unlimited |
| Transaction parsing (text/audio) | ✅ | ✅ |
| Receipt photo OCR | 1/day | Unlimited |
| PDF statement ingestion | 5/month | Unlimited |
| In-chat charts (basic) | ✅ | ✅ |
| **Decision simulation (S3)** | 1/month (teaser) | Unlimited |
| Web dashboard (read-only, post-MVP) | ✅ | ✅ |
| History depth | 12 months | Unlimited |
| Proactive nudges (S8, post-MVP) | ❌ | ✅ |
| Investment benchmark vs. CDI/IPCA/IBOV | ❌ | ✅ |
| Annual patrimonial narrative | ❌ | ✅ |
| RAG over personal history | ❌ | ✅ |

### 7.3 Billing Stack — Decision Locked

**Provider:** Mercado Pago (Pix Automático).
**Rationale:** 0% Pix fees up to R$15k MRR (vs. R$1.99/transaction on Asaas post-promo, vs. Stripe Brasil cross-border IOF 3.5%). Pix Automático regulatory mandate (BCB) effective **2026-05-14** — first-class platform integration.
**Switch trigger:** Re-evaluate Asaas at R$15k+ MRR (~1,520 Pro users) for dunning-automation parity.

---

## 8. Non-Functional Requirements

### 8.1 Service Level Objectives (SLOs)

| Component | Metric | Target | Error Budget |
|---|---|---|---|
| Telegram bot availability | Uptime monthly | 99.5% | 3h 39min/mo |
| Conversational p50 latency | text intent → response | ≤ 1,200 ms | — |
| Conversational p95 latency | text intent → response | ≤ 3,000 ms | — |
| Conversational p99 latency | text intent → response | ≤ 6,000 ms | — |
| PDF ingestion latency | upload → confirmation | ≤ 30 s (synchronous) / async beyond | — |
| Chart generation latency | request → image | ≤ 4,000 ms | — |
| Spring Boot Finance API | Uptime monthly | 99.7% | 2h 11min/mo |
| LLM fallback engagement rate | Gemini failure → GPT-4o-mini | < 2% of requests | — |
| Parser F1 (text transactions) | accuracy on holdout set | ≥ 95% | — |
| Parser F1 (PDF statements, top-5 BR banks) | accuracy on holdout set | ≥ 90% | — |

### 8.2 Security & Trust Boundaries

| Control | Implementation | Risk Mitigated |
|---|---|---|
| Auth: short-lived JWT (15 min) + refresh | Spring Boot issues; Python validates | Token leak |
| Inter-service mTLS | Cert pinning Railway-to-Railway | MITM |
| Rate limiting (per user_id) | Spring: Bucket4j; Python: slowapi | Abuse, runaway COGS |
| Idempotency keys on POST | `Idempotency-Key` header on all writes | Double-write/double-charge |
| Audit log (structured) | user_id, intent, input hash, output hash, ts | LGPD traceability, debug |
| Two-sided output validation | Pydantic (Python) + Jakarta Bean Validation (Spring) | Trust-boundary violation |
| Zero Data Retention via Vertex AI | Enterprise tier, São Paulo region | LLM training-data leakage |

### 8.3 Capacity Planning (MVP — Day 90 baseline)

| Resource | Provisioned | Justification |
|---|---|---|
| Python Agent (Railway) | 1 instance, 512 MB | Stateless; horizontal scale-out at >50 RPS |
| Spring Boot (Railway) | 1 instance, 1 GB | Event-driven; CPU-bound only during projections |
| Supabase | Free tier → Pro at 500 paid users | pgvector + RLS as primary tenant isolation |
| Redis (rate limit) | 256 MB | Counters, TTL-based |

---

## 9. System Architecture (Overview)

### 9.1 Service Topology

```
┌─────────────┐     webhook     ┌─────────────────────┐
│  Telegram   │ ───────────────►│  Python Agent       │
│  Bot API    │ ◄────────────── │  (Pydantic AI)      │
└─────────────┘    sendMessage  │                     │
                                │  - intent routing   │
                                │  - parser           │
                                │  - tool call disp.  │
                                └─────────┬───────────┘
                                          │ HTTPS + JWT
                                          ▼
                                ┌─────────────────────┐
                                │  Spring Boot        │
                                │  Finance API        │
                                │                     │
                                │  - balance engine   │
                                │  - simulation eng.  │
                                │  - chart worker     │
                                │  - billing (MP)     │
                                │  - audit log        │
                                └─────────┬───────────┘
                                          │
                                          ▼
                                ┌─────────────────────┐
                                │  Supabase           │
                                │  (Postgres + RLS    │
                                │   + pgvector)       │
                                └─────────────────────┘
```

### 9.2 Architectural Commitments

| Decision | Choice | Reversal Cost |
|---|---|---|
| RPC protocol Python↔Spring | REST + JWT (not gRPC) | Low — migrate when >10k Pro |
| Orchestration framework | Pydantic AI | Medium — Python re-implementation if abandoned |
| LLM provider (primary) | Google Gemini via Vertex AI | Low — FallbackModel pattern keeps OpenAI viable |
| Database | Supabase (Postgres + RLS) | Medium — standard SQL eases migration to Neon/RDS |
| Predictive engine | Spring Application Events + `@Async` | Low — pattern is idiomatic Spring |
| Multi-model strategy | Flash Lite (conversation) + Flash (PDF) | Low |

### 9.3 Event-Driven Pattern (Proactive AI — S8)

```
Supabase write (transaction inserted)
        │
        ▼
TransactionCreatedEvent (Spring Application Event)
        │
        ▼
@EventListener @Async → BalanceProjector.recalculate(userId)
        │
        ▼ if projection.willGoNegativeBefore(monthEnd)
        ▼
MitigationAgent.proposeFix(userId, projection)
        │
        ▼
Webhook → Python Agent → Telegram sendMessage (proactive nudge)
```

---

## 10. AI Quality & Guardrails

### 10.1 Hard Rules (Engineering Contract)

| Rule | Enforcement | Test Type |
|---|---|---|
| **R1** — LLM never performs balance arithmetic | All math via Spring tool calls | Integration test: assert zero arithmetic in LLM output for 100-msg corpus |
| **R2** — Investment deposits classified as expenses → blocked | Pydantic `@output_validator` raises `ModelRetry` | Unit test: 20 known aporte patterns |
| **R3** — Values > R$ 100,000 require explicit user confirmation | Output validator + confirmation flow | E2E test |
| **R4** — Parser confidence < 0.85 → agent asks clarifying question | Confidence threshold in agent config | Integration test |
| **R5** — Maximum 2 retries on tool calls (Pydantic AI) | `retries=2` on Agent + all tools | Configuration audit |
| **R6** — All tool calls log: user_id, intent, input hash, output hash, ts | Spring AOP interceptor | Log inspection |
| **R7** — No stack traces ever exposed to chat | Spring `@ControllerAdvice` graceful degradation | Negative test: force 500 → assert friendly message |

### 10.2 Multi-Model Strategy

| Workload | Model | Reasoning |
|---|---|---|
| Conversational + tool calls | **Gemini 2.5 Flash Lite** | $0.10/$0.40 per M tokens; 5.4× cheaper than Flash regular; sufficient for structured output |
| PDF statement parsing (5–20 pages) | **Gemini 2.5 Flash** | 1M context covers full statements without chunking; superior multimodal accuracy on degraded scans |
| Receipt photo OCR | **Gemini 2.5 Flash** | More robust than Lite on noisy images |
| Fallback (Gemini outage) | **GPT-4o-mini** | Pydantic AI `FallbackModel`; engaged on 4xx/5xx, not on validation errors |

### 10.3 Evaluation Set Requirements (Pre-Launch Gate)

- **Conversational corpus:** 500 real Brazilian transaction utterances (text + audio), human-labeled.
- **PDF corpus:** 50 statements across top-5 BR banks (Itaú, Bradesco, Nubank, Banco do Brasil, Inter).
- **Adversarial corpus:** 100 ambiguous/edge cases (negative values, foreign currencies, splits, refunds).
- **Pass threshold:** F1 ≥ 95% text, ≥ 90% PDF, ≥ 80% adversarial.

---

## 11. Regulatory Compliance

### 11.1 LGPD (Lei 13.709/2018) — Already In Force

| Requirement | Implementation | Owner |
|---|---|---|
| Legal basis declared | Consent (Art. 7, I) + contract performance (Art. 7, V) | Legal/PM |
| RIPD (DPIA) documented | Risk-mapped pre-launch, reviewed quarterly | Legal/PM |
| Data minimization | LLM context window strictly bounded; no historical data beyond necessity | Engineering |
| Data subject rights | `/cancel` triggers full deletion within 15 days | Engineering |
| Subprocessor disclosure | Google (Vertex AI), Telegram, Mercado Pago, Supabase, Railway, Vercel | Legal/PM |
| Sensitive data inference | Health-keyword transactions excluded from app logs | Engineering |

### 11.2 PL 2338/2023 — Not Yet In Force (Estimated Vacatio Legis: 2028–2029)

**Pre-classification (Luci as low-to-moderate risk):**
- Does not make automated decisions affecting fundamental rights — user approves all actions.
- Does not extend credit, perform health/justice/HR decisions.
- No biometric surveillance.

**Forward-looking commitments built in:**
- Explainability: chain-of-thought reasoning surfaced when user asks "por quê?".
- Human oversight: any action moving funds requires explicit confirmation step (`DeferredToolRequests` pattern).

### 11.3 EU AI Act — Geoblock Recommended

EU residents geoblocked at signup until Article 6 high-risk classification of personal finance agents is clarified in the Brazilian context. Revisit M6.

---

## 12. Risks & Assumptions

### 12.1 Risk Matrix

| ID | Risk | Probability | Impact | Mitigation |
|---|---|:---:|:---:|---|
| R1 | CloudWalk launches Pierre Pro/Família in 2026 | High | High | Ship anchor feature (S3) and visual diff vs Pierre as marketing wedge |
| R2 | Jota.ai expands to consumer finance with free tier | Medium | High | Monetize depth (simulations, narratives), not basic tracking |
| R3 | Pix Automático UX inconsistent at smaller banks | Medium | Medium | Backup: Pix QR with monthly auto-reminder |
| R4 | Gemini API outage / quota issue | Low | High | `FallbackModel` to GPT-4o-mini pre-wired |
| R5 | Supabase pricing changes | Low | Medium | Standard SQL maintained; migration path to Neon/Railway PG |
| R6 | ANPD audits early-stage startup | Low | Medium | LGPD compliance checklist clears ~90% of risk |
| R7 | Banco Central restricts PDF parsing of bank statements | Medium | High | Strictly user-uploaded; never scraping |
| R8 | LLM hallucinates math despite guardrails | Medium | Critical | R1 enforcement + integration tests; canary deploys |
| R9 | Free-tier abuse driving COGS | Medium | Medium | Rate limits + intelligent input rejection (large PDFs) |

### 12.2 Key Assumptions (Validate Before Launch)

| ID | Assumption | Validation Plan |
|---|---|---|
| A1 | Gemini 2.5 Flash Lite hits ≥95% F1 on BR PT-BR transactions | Eval set run before code freeze |
| A2 | Mercado Pago Pix Automático sandbox supports full subscription lifecycle | Sandbox spike in week 1 |
| A3 | Telegram is acceptable channel for primary persona | User interviews (n=20) + landing page conversion test |
| A4 | R$ 9.90 pricing yields ≥4% Free→Pro conversion | A/B test post-launch at week 6 |
| A5 | 8 BR banks' PDF format stable enough to parse | Eval set in week 2 |

---

## 13. Quality Standards (Anti-Vibe Rules)

| Rule | Enforcement |
|---|---|
| Strict TypeScript / Python (no `any`, no `# type: ignore` without ticket) | CI lint blocks merge |
| Pydantic schemas with strong validators (Decimal for money, never float) | Code review checklist |
| Explicit exception handling: `HTTPError`, `ValidationError`, `TimeoutException` | CI scan for bare `except:` |
| Graceful degradation: never expose stack traces in chat | Negative test in CI |
| No AI hallucinations for math (R1) | Integration test in CI |
| Idempotency on every write | Schema-level check |
| Audit log on every tool call | AOP-enforced |

---

## 14. Definition of Done (Launch Gate)

### 14.1 Functional

- [ ] Stories S1–S7 execute end-to-end in production environment.
- [ ] Free tier rate limits enforced and observable.
- [ ] `/cancel` flow purges all user data within 15 days (verified with test user).
- [ ] Pix Automático subscription lifecycle tested in sandbox AND production with R$0.01 test charges.

### 14.2 Non-Functional

- [ ] p95 latency ≤ 3,000 ms verified under 50 RPS synthetic load.
- [ ] Parser F1 ≥ 95% on text eval set, ≥ 90% on PDF eval set.
- [ ] LLM COGS ≤ R$ 0.42/Pro user/mo under load test (1,000 simulated users × 30 days).
- [ ] Zero `console.log`, zero raw stack traces in user-visible paths.
- [ ] `FallbackModel` engagement verified by forced Gemini failure scenario.

### 14.3 Compliance

- [ ] RIPD documented and signed off (internal).
- [ ] Public Terms of Use published with subprocessor list.
- [ ] LGPD data subject rights endpoint (`/lgpd/export`, `/lgpd/delete`) tested.
- [ ] Cancellation flow audited end-to-end (chat → billing revocation → deletion).

### 14.4 Operational

- [ ] Logging: structured JSON, 100% audit coverage on tool calls.
- [ ] Alerting: SLO breaches paged to founder (PagerDuty or equivalent).
- [ ] Runbook: top-10 incident scenarios documented.
- [ ] Rollback plan: tested git revert + DB migration rollback.

---

## 15. Post-MVP Roadmap (Indicative)

| Phase | Trigger | Investment |
|---|---|---|
| **Phase 1 — Activation (M0–M3)** | Launch → 100 Pro | Marketing reinvestment 100%; iterate on S1–S7 quality |
| **Phase 2 — Depth (M3–M6)** | 100 → 500 Pro | S8 proactive nudges; investment tracking (S9); web dashboard read-only |
| **Phase 3 — Aggregation (M6–M12)** | 500 → 1,000+ Pro | Open Finance via Pluggy (now economically viable); WhatsApp channel evaluation |
| **Phase 4 — Scale (M12+)** | 1,000+ Pro, MRR > R$15k | Asaas evaluation; native mobile app if Telegram friction >15%; ITP licensing eval |

---

## 16. Appendix A — Unit Economics Model

### A.1 Per-User Monthly COGS (Pro)

| Component | Cost (R$) | Source |
|---|---|---|
| Pix processing (Mercado Pago, <R$15k MRR) | 0.00 | Mercado Pago official |
| LLM inference (Flash Lite + Flash mix) | 0.42 | Research: 1,500 msgs + 10 PDFs + 60 photos |
| Infra rateado (Railway + Supabase + Vercel) | 0.80 | At ~250 Pro |
| Object storage (chart PNGs) | 0.10 | Estimate |
| Compliance amortized (audit, RIPD) | 0.15 | Estimate |
| **Total COGS** | **1.47** | |
| **Gross Margin** | **R$ 8.43 (85.2%)** | (9.90 - 1.47) / 9.90 |

### A.2 Sensitivity Analysis

| Scenario | LLM COGS | Total COGS | Margin |
|---|---|---|---|
| Optimistic (heavy Flash Lite, light PDF) | 0.28 | 1.33 | 86.6% |
| **Base (mixed)** | **0.42** | **1.47** | **85.2%** |
| Pessimistic (heavy PDFs, photo-heavy) | 0.70 | 1.75 | 82.3% |
| Adversarial (Pluggy added prematurely) | 0.42 + 2.50 = 2.92 | 4.25 | 57.1% — **destroys margin** |

**Conclusion:** Margin floor of 80% holds in all base/pessimistic scenarios. Open Finance must remain out-of-scope until 1,000+ Pro users.

---

## 17. Appendix B — Open Questions

| # | Question | Owner | Due |
|---|---|---|---|
| Q1 | Final naming — Lúcido confirmed? Domain + INPI cleared? | Founder | Pre-Tech-Design |
| Q2 | Telegram-only acceptable for primary persona? | Founder | User interviews week 1 |
| Q3 | Receipt photo OCR — is it MVP-critical or P1? | Founder | Pre-Tech-Design |
| Q4 | Investment tracking S9 — MVP or post-MVP? | Founder | Pre-Tech-Design |
| Q5 | Confidence threshold for clarifying-question prompt — 0.85 or tuneable? | Engineering | Eval set week 2 |

---

**End of PRD v2.0.**

> Approval signals:
> - [ ] Founder / Tech Lead
> - [ ] Legal/Compliance review (LGPD)
> - [ ] Engineering review (Architecture)

[Confidence: High | Requires external verification: No]
