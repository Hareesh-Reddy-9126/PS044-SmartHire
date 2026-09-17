# Phase 5 — Implementation Roadmap — PS044-SmartHire

_From an empty repository to a deployed, live system. **Planning only — no application code, no
services, no skeleton, no new tools in Phase 5.** Authoritative inputs: approved Phase-3
architecture (`../architecture/phase-3-technical-architecture.md` + `../architecture/adrs.md`),
approved Phase-4 governance (`../governance/phase-4-engineering-governance.md`), and
`../traceability-matrix.md`. Architectural decisions are **used as-is** here; any change would
require a superseding ADR + approval (governance §39/§42)._

## Classification legend

🟦 SOURCE REQUIREMENT · 🟩 APPROVED ENHANCEMENT · ✅ DECISION · 🟨 ASSUMPTION · 🟥 OPEN QUESTION.

## Optimization order (ties every sequencing choice below)

1. **Correctness + academic compliance first** — the 🟦 mandatory slice (Inc 0–4, §49 gate) is
   built and green before any 🟩 enhancement.
2. **Incremental vertical slices** — each increment is independently demonstrable and verified.
3. **Automated verification alongside code** — tests land *with* the feature, never after.
4. **Security & maintainability built-in** — not a later "hardening phase."
5. **Minimal complexity** — enhancements enter only at their justified point (Redis, MinIO, AI…).

---

## §0. Which service is implemented first, and why — ✅

**Order:** `service-registry` (Eureka) → `api-gateway` → `common` → `auth-service` →
**`job-service`** → `application-service` → `recruitment-service` → enhancements.

- Infra-first (**Eureka, Gateway**) because everything registers/routes through them (🟦 C2/C3).
- **`job-service` is the first *domain* service** because it is the **leaf of the mandated
  Application→Job→Recruitment chain (🟦 C5/R10)** — it has **no outbound inter-service
  dependency**, so it can be built and fully tested standalone, and it is exactly what
  `application-service` must validate against. Building the leaf first means every later service
  finds its dependency already implemented and testable. (`auth-service` precedes it only because
  protected endpoints need JWT issuance to be meaningful.)

---

## A. Master implementation sequence

Increments **Inc 0 → Inc 8**. Inc 0–4 = the 🟦 mandatory R1 slice. Inc 5–6 = 🟩 R1 enhancements +
hardening. Inc 7–8 = R2/R3 + production/handover. **Each increment ends at a STOP gate (§42).**

| Inc | Name | Class | Depends on | Primary output |
|---|---|---|---|---|
| **0** | Microservices skeleton | 🟦 | — | parent build, `common`, Eureka, gateway, job-service reachable through gateway |
| **1** | Auth + JWT + edge security | 🟦 | 0 | auth-service (RS256/JWKS), gateway JWT validation, RBAC, Redis (revoke/rate-limit) |
| **2** | Job service (full) | 🟦 | 1 | job posting/listing/search/close/reopen + Job state machine |
| **3** | Application service + inter-service + LB | 🟦 | 2 | apply/status APIs, Feign→Job validate, ≥2 job instances load-balanced |
| **4** | Recruitment service | 🟦 | 3 | shortlist, interview/final status, status tracking, App→Recruitment link |
| **5** | R1 enhancements (thin) | 🟩 | 4 | document-service, notification-service (Redis Streams), SPA core, audit, OpenAPI, Sentry |
| **6** | Test + deploy hardening | 🟦+🟩 | 5 | contract tests, full Compose, CI wired, E2E core journeys |
| **7** | AI decision-support | 🟩 | 6 | ai-service (Claude), matching/summaries, eval harness — advisory/HITL (R2) |
| **8** | Production + handover | 🟩 | 6(→7) | cloud/K8s target, secrets store, tracing, prod readiness, live handover |

### Per-increment detail

**Inc 0 — Skeleton (🟦).**
Creates: root `pom.xml` (BOM/plugin mgmt), `common` (Problem-Details base, correlation-id filter,
DTO/base types), `service-registry` (Eureka Server), `api-gateway` (routes, CORS, security headers,
`X-Correlation-Id`), `job-service` with a real `GET /api/v1/jobs` backed by `job_svc.V1__jobs.sql`
(source fields). Infra/Compose: `eureka + gateway + job-service + postgres`. Tests: **Testcontainers
(Postgres)** integration test + a routing test (gateway→eureka→job). Verify: request routes through
the gateway and resolves job-service via discovery (no hard-coded host).
Class notes: Postgres/Compose 🟩; discovery/gateway 🟦.

**Inc 1 — Auth + JWT (🟦).**
Creates: `auth-service` (`auth_svc.V1`: users, organizations, refresh_tokens), register/login,
**RS256** issuance + **JWKS**, refresh rotation; gateway **JWT validation** (deny-by-default,
public allow-list); `@PreAuthorize` role scaffolding + object-ownership helper in `common`.
**Redis introduced here** for **exactly two justified uses**: gateway **rate limiting** + refresh/`jti`
**revocation denylist** (ADR-0011) — **no caching yet**. Compose: `+redis`. Tests: unit (token
issue/verify, password hashing, revocation), integration (Testcontainers Postgres+Redis; protected
route 401 without token, role honored). **Security checkpoint #1** (auth review).

**Inc 2 — Job service full (🟦).**
Creates: job posting/listing/**search+filter**(🟩)/close/reopen APIs; **Job state machine**
`DRAFT→OPEN↔CLOSED` (D5) with server-validated transitions; `job_svc.V2` (status, indexes).
Tests: unit (every legal + illegal transition), integration (persistence, authz: only owning
org recruiter mutates). Acceptance: **AC-2**.

**Inc 3 — Application + inter-service + load balancing (🟦).**
Creates: `application-service` (`application_svc.V1`: applications, outbox), apply/list/status APIs;
**OpenFeign → job-service validate** (job exists & OPEN) wrapped in **Resilience4j** (timeout +
circuit breaker, **fail-closed**); Application state machine (D5); **apply-once** rule (D10).
**Contract testing introduced** (Spring Cloud Contract on App↔Job). **Load-balancing demo:**
`docker compose up --scale job-service=2`; Feign resolves `lb://JOB-SERVICE`; integration test +
log/metric evidence that both instances receive validate calls. Acceptance: **AC-3, AC-4**.
**Security checkpoint #2** (authz/ownership + input validation).

**Inc 4 — Recruitment (🟦).**
Creates: `recruitment-service` (`recruitment_svc.V1`: recruitments, interviews, outbox); shortlist,
**separate** interview_status + final_status machines (D5); status tracking reads; App→Recruitment
advance (Feign, on shortlist). Tests: unit (both machines, separation), integration. Acceptance:
**AC-5**. **→ End of the 🟦 mandatory slice — first Academic Demonstration Checkpoint (§ D).**

**Inc 5 — R1 enhancements, thin (🟩).**
Creates: `document-service` (`document_svc.V1`; MinIO upload→store→**basic** parse; pre-signed URLs;
MIME/size/AV-hook validation) → sets `resume_link`; `notification-service` (**Redis Streams**
consumer; email via MailHog); **transactional outbox relay** wired in application/recruitment; audit
consumer (append-only); **SPA** (Vite/React/TS) for core journeys; **OpenAPI** published per service;
**Sentry + structured logging + Actuator health** (observability checkpoint). Compose:
`+minio +mailhog +frontend`. **Playwright E2E introduced** (core journeys). Acceptance: **AC-6, AC-7**.
**Security checkpoint #3** (upload security, PII, pre-signed URLs).

**Inc 6 — Test + deploy hardening (🟦+🟩).**
Full contract coverage (App↔Job, App↔Recruitment), Testcontainers coverage to target (§21), full
`docker compose up` healthy system (**AC-8**), **CI wired** (once OQ-CI resolved) running the full
gate (§27/§29). **Production-readiness checkpoint (R1).** **→ R1 complete: full §49 compliance gate
must be green.**

**Inc 7 — AI decision-support, R2 (🟩).**
Creates: `ai-service` (Claude), matching/ranking + summaries + JD assist (advisory, **HITL**), owns
MatchScore; **evaluation harness** (golden sets, safety/bias, regression) gates release; PII
redaction, prompt-injection defenses, protected-attributes excluded, output schema-validated (D9,
governance §43). `config-server` if multi-env config now justifies it. Acceptance: **AC-9** (AI never
auto-decides). **Security checkpoint #4** (AI data-handling, eval sign-off).

**Inc 8 — Production + handover (🟩 / 🟥 target).**
Cloud/K8s target selection (resolves **OQ7**), managed **secrets store** (OQ-KEY), distributed
**tracing** (OpenTelemetry), TLS termination, backups, **production-readiness checklist** (§47),
runbook, then **live handover** (§ G). R3 items (analytics, semantic search, fairness monitoring,
DAST/pentest, perf/load testing) scheduled here.

---

## B. Dependency graph

```
service-registry (Eureka) ──────────────────────────────────┐ (all register)
        │                                                    │
        ▼                                                    │
   api-gateway ◄── common (Problem-Details, JWT utils, correlation-id, event schemas)
        │                                                    │
        ▼                                                    │
   auth-service ──(JWKS/JWT)──► [every resource service verifies offline]
        │                                                    │
        ▼                                                    │
   job-service  (leaf: no outbound service dep) ◄────────────┘
        ▲
        │ Feign validate (job OPEN?)          Redis: rate-limit + revoke (Inc1), Streams (Inc5)
   application-service ──(outbox event)──► Redis Streams ──► notification-service (email)
        │                                                └─► audit consumer
        │ Feign advance (on shortlist)
        ▼
   recruitment-service
        ⋮
   document-service (MinIO)   [independent; application stores resume_link]
   ai-service (R2) ──reads application/job data, calls Claude── advisory only
```
**Build rule:** never start a node before all its inbound-dependency nodes are Done (§C).

---

## C. Increment-by-increment Definition of Done

Baseline DoD (governance §32) applies to **every** increment: compiles; unit+integration (and
contract/E2E where applicable) green; security & authz reviewed; input validated; no secrets/PII
leaked; RFC 9457 errors; OpenAPI + docs updated; ArchUnit/fitness pass; **traceability matrix
updated**; `docker compose up` healthy for integration-affecting changes; bug fixes carry a
failing-first regression test. **Plus per-increment:**

| Inc | Increment-specific "Done" |
|---|---|
| 0 | Request routes gateway→Eureka→job-service; Testcontainers Postgres test green; no hard-coded hosts. |
| 1 | Unauthenticated protected route → 401; valid role JWT authorized; token revocation works; rate limit active. (**AC-1**) |
| 2 | Recruiter posts a job → `OPEN`, appears in listings; illegal transitions rejected 409/422. (**AC-2**) |
| 3 | Candidate applies to OPEN job → Application created; closed/absent job rejected via Feign; **LB across ≥2 job instances proven**; apply-once enforced. (**AC-3, AC-4**) |
| 4 | Shortlist + interview/final status transitions persist and are trackable; interview/final kept separate. (**AC-5**) |
| 5 | Résumé upload sets `resume_link` + basic parse; email sent on status change; audit rows written; SPA drives core flows; OpenAPI live; Sentry receiving. (**AC-6, AC-7**) |
| 6 | Contract tests guard both Feign boundaries; `docker compose up` = whole system healthy; CI gate green end-to-end. (**AC-8**) |
| 7 | AI outputs are advisory with rationale + model/version metadata; **no status changes without a human action**; eval harness passes before enable. (**AC-9**) |
| 8 | §47 production-readiness checklist fully green in the target environment; runbook + handover accepted. |

---

## D. Verification gates (independent verification after each increment)

Every increment is verified **independently of the author's assertion** (governance §41/§44 — AI
claims are not trusted, they are checked):

1. **Automated gate:** the full CI/local suite (build → unit → integration/Testcontainers →
   contract → format/lint → security scan → ArchUnit). Red = not Done.
2. **Acceptance gate:** the mapped AC (above) is executed and observed.
3. **Traceability gate:** the affected rows in `../traceability-matrix.md` are mapped **and green**;
   an unmapped mandatory requirement = drift = blocked (§46/§49).
4. **Human approval gate (STOP):** owner reviews and approves before the next increment.

**Academic demonstration checkpoints (🟦):**
- **DC-1 (after Inc 4):** live demo of the full mandatory slice — JWT login (both roles) → gateway
  routing → Eureka discovery → post job → apply (inter-service validate) → **≥2 instances
  load-balanced** → shortlist → status tracking; unit+integration tests green. This alone satisfies
  the Project 44 grade.
- **DC-2 (after Inc 6):** `docker compose up` full system + contract/E2E green + CI report.

**Production-readiness checkpoints:** R1 at Inc 6 (§47 minus cloud items), full at Inc 8.

---

## E. Risk register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| RK-1 | Microservice moving parts overwhelm the timeline | Med | High | Vertical slices; mandatory slice (Inc 0–4) first; enhancements strictly deferred |
| RK-2 | Load-balancing demo flaky/unconvincing | Med | High (graded) | Scripted `--scale` demo + automated LB assertion in Inc 3; log/metric evidence |
| RK-3 | Testcontainers slow/unstable in CI | Med | Med | Reuse + singleton container base; pinned images; run heavy ITs on a labeled stage |
| RK-4 | JWT key management / rotation (OQ-KEY) | Med | High | Keys via env/secret store, never committed; rotation policy resolved by Inc 8 |
| RK-5 | Inter-service coupling / cascading failure | Med | Med | Resilience4j timeouts+breaker, **fail-closed** apply; async elsewhere (outbox) |
| RK-6 | AI scope creeps into the mandatory path | Med | High | Hard gate: AI is R2, off critical path; R1 demonstrable without it (D8, §49) |
| RK-7 | PII / résumé data mishandling | Low | High | PII minimized/redacted pre-AI; pre-signed URLs; no PII in logs; audit (§18/§20/§43) |
| RK-8 | OQ-CI unresolved blocks the CI gate | Med | Med | Same gates run locally via `mvn verify` until platform confirmed; wire at Inc 6 |
| RK-9 | Unknown grading rubric/deadline (OQ9) | Med | Med | Source = minimum baseline; mandatory slice front-loaded so it's always demoable |
| RK-10 | Redis single instance = SPOF for events/rate-limit | Low | Med | Acceptable in R1 (documented ceiling); HA/broker upgrade path in R3 (ADR-0012) |
| RK-11 | Enhancement sprawl (new deps/services) | Med | Med | "Do not build yet" list (F) + governance §28; new dep needs PR justification |

---

## F. "Do not build yet" list (dependencies/tools/services deliberately deferred)

**Do NOT add until its stated trigger** (adding earlier violates governance §28 and the owner
directive):

- **Kafka / RabbitMQ** — Redis Streams suffices for R1/R2; add only if durability/throughput demands (R3, ADR-0012). 🟥
- **Claude SDK / `ai-service`** — R2 (Inc 7). Not in the mandatory slice. 🟩
- **`config-server`** — R2, only when multi-environment config justifies it (env vars until then). 🟩
- **`analytics-service`, vector/semantic search, data warehouse** — R3. 🟩
- **Kubernetes / Helm / service mesh** — until the cloud target is chosen (OQ7, Inc 8). 🟥
- **Managed secrets store (Vault/KMS)** — with the deployment target (Inc 8/OQ-KEY). 🟥
- **Distributed-tracing backend (OTel collector/Jaeger)** — R2/R3; correlation-id carries R1. 🟩
- **CodeQL / DAST / pentest tooling** — R3 (SpotBugs + Dependency-Check + gitleaks cover R1). 🟩
- **Lombok** — not in R1 (records suffice); only if boilerplate measurably hurts (§4). ✅
- **Next.js / SSR** — SPA only unless SEO/marketing pages appear (ADR-0014). ✅
- **Saga/orchestration framework** — no cross-service write transaction exists in R1 (ADR-0018). ✅
- **Separate databases per service (physical)** — schema-per-service until a service needs independent scaling (ADR-0008). ✅
- **Second human reviewer / heavy branch protection** — team is solo+AI; revisit if the team grows (§31). 🟨

---

## §31/Rollback strategy — ✅

Nothing is live before Inc 8, so rollback is defined for the deploy era and rehearsed in Compose:

- **Immutable, versioned images** (SemVer + git SHA, §33/§34): roll back = redeploy the previous
  tag. `latest` never relied upon.
- **Forward-only DB migrations** (Flyway, §12): never a destructive down-script. Schema changes use
  **expand → migrate → contract** so an app rollback stays compatible with the current schema; a bad
  migration is corrected by a **new compensating migration**, not by editing history.
- **Decoupled side effects:** outbox/Streams consumers are idempotent and replayable; a
  notification/audit failure never blocks or corrupts the core transaction.
- **Enhancement kill-switch:** risky 🟩 features (esp. AI, Inc 7) sit behind config flags so they
  can be disabled without redeploy; the mandatory path never depends on them.
- 🟥 Cloud-specific rollback (blue/green vs rolling, DB snapshot/PITR) is fixed with the target (Inc 8).

---

## G. Final path: empty repository → deployed production system

```
[empty repo]
   │  Inc 0  scaffold: parent + common + Eureka + gateway + job-service (routing proven)   🟦
   │  Inc 1  auth-service + JWT/JWKS + gateway validation + Redis(revoke/rate-limit)        🟦  ▶ STOP/approve · AC-1
   │  Inc 2  job-service full + Job state machine                                            🟦  ▶ STOP · AC-2
   │  Inc 3  application-service + Feign→Job + Resilience4j + LB(≥2)                          🟦  ▶ STOP · AC-3,AC-4
   │  Inc 4  recruitment-service + shortlist + status tracking                               🟦  ▶ DC-1 demo · §49 mandatory slice DONE
   │  Inc 5  document + notification(Streams) + SPA + audit + OpenAPI + Sentry               🟩  ▶ STOP · AC-6,AC-7
   │  Inc 6  contract + E2E + full Compose + CI gate                                         🟦+🟩 ▶ DC-2 · R1 production-ready · AC-8
   │  Inc 7  ai-service (Claude, advisory, HITL) + eval harness                              🟩(R2) ▶ STOP · AC-9
   │  Inc 8  cloud/K8s target + secrets store + tracing + prod readiness (§47)               🟩/🟥  ▶ STOP
   ▼
[deploy] build-once images → target env → smoke test (health + core journey) → observability live
   ▼
[handover]  runbook + ADR/architecture/governance/traceability docs + OpenAPI + demo creds recipe
            + on-call/incident expectations (§48) + rollback procedure → owner sign-off
   ▼
[live]  monitored (Sentry/metrics/tracing), CI-guarded, drift-checked (traceability matrix)
```

**Handover deliverables (Inc 8):** running system in the target env; `/docs` (discovery →
architecture → governance → **traceability** → roadmap); per-service README + published OpenAPI;
`docker-compose.yml` for local bring-up; runbook (start/stop, config keys, rollback, incident
triage); the green compliance-gate report; **no secrets in any artifact** (§17).

---

## Requested-item coverage (1–34)

| # Item | Where |
|---|---|
| 1 Phases/increments · 2 Dependency order · 3 What's created · 4 First service+why | §0, A, B |
| 5 Module structure evolution | A (per-inc "Creates") + governance §2 |
| 6 Schema creation order · 7 Migration strategy | A (V1/V2 per inc), Rollback, gov §12 |
| 8 API order · 9 Gateway order · 10 Eureka order · 11 JWT order | A: Eureka/Gateway Inc 0, JWT Inc 1, APIs Inc 2→4 |
| 12 Inter-service order · 13 Load-balancing demo | Inc 3 (Feign + `--scale` demo, AC-4) |
| 14 Redis point + justified uses | Inc 1 (rate-limit+revoke), Inc 5 (Streams); no caching |
| 15 Document/resume point · 16 Notification point | Inc 5 |
| 17 Frontend order | Inc 5 (backend-first; APIs demoable without SPA) |
| 18 Testing alongside · 19 Testcontainers point · 20 Contract/E2E points | A + C + D; TC Inc 0, Contract Inc 3, E2E Inc 5 |
| 21 Security checkpoints · 22 Observability checkpoints | A (#1–#4), Inc 5 observability, D |
| 23 CI/CD order · 24 Docker/Compose evolution | Inc 0→6 (local gate→CI at 6); Compose grows per inc |
| 25 Local dev workflow | §35 gov + `docker compose up`/`mvn verify` per inc |
| 26 Agent usage per phase | §"AI-agent usage" below |
| 27 Independent verification · 28 Academic demo · 29 Prod-readiness | D (gates, DC-1/DC-2, §47) |
| 30 Deploy/handover · 31 Rollback · 32 Per-inc DoD | G, Rollback, C |
| 33 Do-not-build-yet · 34 Stop/approval gates | F; STOP gates in A/D/G (§42) |

### AI-agent / subagent usage per phase (item 26) — ✅/🟨

Main agent orchestrates and holds context; **subagent output is always verified before use**
(governance §41); no agent self-approves a gate or merges (§42).

- **Every increment (before building):** `superpowers:brainstorming` then plan; `Plan` subagent for
  step design; `Explore` (read-only) for locating patterns.
- **Inc 0–4:** `general-purpose` for Spring Cloud/config research; **code-review** plugin runs an
  adversarial review pre-merge; **systematic-debugging** on any failure (root-cause, §48).
- **Inc 5:** `frontend-design` for the SPA; `redis-development` for Streams; document-processing
  skill for résumé parsing.
- **Inc 7 (AI):** `llm-evaluation` for the eval harness, `llm-security` for prompt-injection/PII
  defenses; `sentry` skill for observability wiring.
- **Human owner:** approves each STOP gate; is the sole authority for scope/architecture changes.

---

## Consistency check vs Phases 3 & 4 (required)

No architectural decision is changed. Ordering **derives from** the approved dependency structure
(ADR-0007 chain → job before application before recruitment; ADR-0005 Eureka first; ADR-0009 auth
before protected APIs; ADR-0011/0012 Redis at its justified points; ADR-0013 MinIO at Inc 5;
ADR-0015 AI at R2/Inc 7). Every gate maps to a governance rule (§21–27 tests, §42 approvals, §46
drift, §49 compliance). Open questions unchanged: **OQ-CI** (CI wiring Inc 6), **OQ7** (target Inc 8),
**OQ-KEY** (Inc 8), **OQ-AI1** (before Inc 7 enable), **OQ9** (source = baseline), **OQ-PKG** (Inc 0
bootstrap). No new architectural decisions introduced — only sequencing.
