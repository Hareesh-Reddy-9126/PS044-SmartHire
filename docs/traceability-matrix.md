# Master Traceability Matrix — PS044-SmartHire

_The canonical cross-phase chain: **Requirement → Architecture → Governance → Tests → Acceptance
Criterion**. This is the drift-detection artifact of record (governance §46/§49): a mandatory row
that loses any link is drift and blocks the R1 release. Updated whenever scope, architecture, or
governance changes._

**Legend:** 🟦 SOURCE · 🟩 APPROVED ENHANCEMENT.
**Source keys:** `project-44-source-requirements.md` (R1–R12, C1–C9).
**Architecture:** `architecture/phase-3-technical-architecture.md` (§) + `architecture/adrs.md` (ADR).
**Governance:** `governance/phase-4-engineering-governance.md` (§).
**Tests:** U=unit · I=integration (Testcontainers) · C=contract (Spring Cloud Contract) · E=E2E (Playwright).
**Acceptance:** `discovery/phase-2-product-model.md` §13 (AC-1…AC-9).

---

## 1. Mandatory (🟦 SOURCE) — the Project 44 compliance gate (§49)

| # | Requirement (source) | Architecture | Governance | Tests | Acceptance |
|---|---|---|---|---|---|
| C1 | Microservices architecture | §1–2 · ADR-0001 | §2,§3,§46 | U,I,C | AC-8 |
| C2 / R7 | API Gateway (routing) | §3 · ADR-0004 | §6,§16 | I,E | AC-1 |
| C3 / R8 | Eureka discovery | §4 · ADR-0005 | §2,§35 | I | AC-4 |
| C4 / R9 | Load balancing | §9 · ADR-0006 | §36 | I | AC-4 |
| C5 / R10 | Inter-service comm (App→Job→Recruitment) | §8 · ADR-0007 | §14,§24,§37 | C,I | AC-3,AC-4 |
| C6 / R6 | JWT auth (recruiters + candidates) | §5–6 · ADR-0009/0010 | §16,§17 | U,I | AC-1 |
| C7 | Relational model (Job/Application/Recruitment) | §7,§26 · ADR-0008 | §12,§13 | I | AC-2,AC-3,AC-5 |
| C8 / R11 | Unit + integration testing | §21 · ADR-0002 | §21–26 | U,I,C | AC-8 |
| C9 / R12 | Deployment | §22,§27 · ADR-0017 | §34,§35,§47 | I,E | AC-8 |
| R1 | Post jobs / manage listings | §2 (job-service) | §6,§10 | U,I | AC-2 |
| R2 | Apply online / manage applications | §2 (application-service) | §6,§10,§13 | U,I | AC-3 |
| R3 | Hiring workflow incl. shortlisting | §2,§26 (recruitment-service) | §22 | U,I | AC-5 |
| R4 | Status tracking | §26 (state machines) | §9,§22 | U,I | AC-3,AC-5 |
| R5 | APIs (posting/submission/status) | §15 · ADR-0016 | §6,§7,§8 | I,E | AC-2,AC-3 |
| — | Job table (job_id, company_name, role, location, status) | §7,§26 | §12 | U,I | AC-2 |
| — | Application table (…, resume_link, status) | §7,§26 | §12 | U,I | AC-3 |
| — | Recruitment table (interview_status, final_status) | §7,§26 | §12 | U,I | AC-5 |
| — | Actors Recruiter + Candidate (JWT) | §6 · ADR-0010 | §16 | U,I | AC-1 |

**Gate rule:** R1 is Done only when **every row above is implemented and its Tests column is green**,
demonstrable **without any AI feature** (governance §49, D8).

**Increment status (Inc 0 and Inc 1 — verified locally; not owner-approved, not merged):** vertical
slices have landed for a subset of these rows. They are **not** Done — the gate rule above is
unchanged and **not yet satisfied**. Delivered and locally verified:

| Row | Inc 0 slice delivered | Verification that actually ran |
|---|---|---|
| C1 | 4 Maven modules (common, service-registry, api-gateway, job-service) as separate deployables | ArchitectureTest — 6 ArchUnit §46 rules, green |
| C2 / R7 | Spring Cloud Gateway routes `/api/v1/jobs/**` → `lb://JOB-SERVICE`, deny-by-default (discovery locator off) | GatewayRoutingTest (U) green + live routed **HTTP 200** |
| C3 / R8 | Eureka server (8761); gateway + job-service register and route via discovery | live end-to-end (docker compose): gateway → Eureka → job-service |
| C7 | **Job** entity + Flyway schema `job_svc` only (Application/Recruitment deferred) | JobApiIT (I, Testcontainers) — runs on CI; **skipped** Docker-less locally |
| C8 / R11 | JUnit 5 unit slice + Testcontainers integration foundation | **11 unit tests green (0 failures)**; JobApiIT skipped locally |
| C6 / R6 | Inc 1 auth-service issues RS256 access tokens and JWKS; gateway validates JWTs with issuer/audience checks and deny-by-default; CANDIDATE/RECRUITER/ADMIN RBAC; Redis revocation/rate limiting; refresh rotation/reuse detection; RFC 9457 security errors | JDK 21 `mvn clean verify`: **65 tests passed**, Spotless PASS, SpotBugs PASS with **0 findings**; AuthApiIT and JobApiIT skipped because Docker is unavailable locally |

Rows C4–C5, C9, R1–R5 and every §3 business rule remain unimplemented (later increments).

## 2. Approved enhancements (🟩) — additive, must not obscure §1

| Enhancement | Architecture | Governance | Tests | Acceptance |
|---|---|---|---|---|
| Web UI (React+TS SPA) | §14 · ADR-0014 | §5 | E | AC-1..AC-7 (via UI) |
| Résumé upload + storage + basic parse | §11 · ADR-0013 | §10,§15 | I,E | AC-6 |
| Notifications (email) | §12 · ADR-0012 | §20 | I | AC-7 |
| Audit logging | §19 · ADR-0012 | §20 | U,I | (BR-6) |
| Observability (Sentry/metrics/health) | §20 | §19 | I | AC-8 (health) |
| Resilience (Resilience4j) | §25 · ADR-0018 | §37 | I | (fail-closed apply) |
| AI matching/summaries (**R2**, advisory, HITL) | §13 · ADR-0015 | §40,§43,§44 | U,I,eval | AC-9 |
| Search & filtering | §2,§15 | §6 | I | — |
| Interview scheduling (**R2**) | §2 (recruitment) | §22 | U,I | — |
| Analytics/dashboards (**R3**) | §2 (analytics-service) | — | — | — |
| CI/CD + security scanning | §23,§29 · ADR-0019 | §27,§29 | (pipeline) | AC-8 |

## 3. Business rules → enforcement → test (locked D5/D10)

| Rule | Architecture (state machine / check) | Governance | Test |
|---|---|---|---|
| Apply only to OPEN jobs; closed jobs reject | §8,§26 (Feign validate + Job SM) | §14 | U,I (fail-closed) |
| One active application per candidate/job | §26 | §10,§22 | U,I |
| Candidate may withdraw while SM permits | §26 (Application SM) | §22 | U |
| Recruiter controlled close/reopen only | §26 (Job SM) | §22 | U |
| Illegal status transitions rejected | §26 · §9 (409/422) | §9,§11,§22 | U |
| Recruiter acts only within own org | §6 (orgId ownership) | §16 | U,I |
| Candidate acts only on own data | §6 (sub ownership) | §16 | U,I |
| AI never auto-decides (advisory only) | §13 (HITL) | §43 | U (AC-9) |
| No protected attributes as AI inputs | §13 · D9 | §43 | U,eval |

## 4. Chain integrity check

- **Every 🟦 mandatory requirement** has a non-empty Architecture, Governance, Tests, and
  Acceptance link → **compliance gate satisfiable**. ✅
- **No enhancement** row maps onto a mandatory component in a way that blocks it (AI is R2 and
  off the critical path; UI wraps but does not gate the APIs). ✅
- **Open questions** (OQ-CI, OQ-KEY, OQ-AI1, OQ7, OQ9, OQ-PKG) touch **no mandatory row's ability
  to be built and verified** — they are platform/deployment/policy details. ✅

_Maintenance: this matrix is updated in the same PR as any change to scope, an ADR, a governance
rule, or an acceptance criterion (governance §32 DoD, §46 drift)._
