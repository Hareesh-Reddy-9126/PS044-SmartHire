# Requirements Register — PS044-SmartHire

_Living document. Separates what is **known**, **inferred**, **unknown**, and **decided**.
Authoritative source detail lives in `project-44-source-requirements.md`._

Status: **Phases 0–6 APPROVED.** Source + 2026 enhancement scope **APPROVED** (D4). Phase-3
decisions locked (D5–D13). Phase-5 approval added the **MinIO scope directive** (D14). Remaining open
questions (OQ-AI1, OQ-CI, OQ-KEY, OQ7 cloud target, OQ9 rubric, OQ-PKG) are Phase-4/deployment items
scheduled into roadmap increments, **not** implementation blockers. **Increment 0 (skeleton)
implemented and verified locally** (Maven multi-module; Eureka registry + Spring Cloud Gateway +
job-service; `GET /api/v1/jobs`; CLIENT → GATEWAY → EUREKA → JOB-SERVICE proven live at HTTP 200;
`mvn clean verify` green on JDK 21). **Increment 1 (authentication and authorization) is
implemented and verified locally**: RS256/JWKS authentication, gateway JWT validation and
deny-by-default, CANDIDATE/RECRUITER/ADMIN RBAC, Redis revocation and rate limiting, refresh-token
rotation/reuse detection, and RFC 9457 security errors. The final JDK 21 `mvn clean verify` passed
65 tests with Spotless PASS and SpotBugs PASS (0 findings); 2 Testcontainers integration tests were
skipped because Docker is unavailable in the local environment. **Awaiting owner approval of Inc 0
and Inc 1; not merged.**

---

## FACTS (verified)

**Repository (Phase 0):**
- F1. Greenfield repo — no application code, build files, infra, DB artifacts, or CI.
- F2. `.gitignore` anticipates Node + Java/Spring Boot; blocks `.env*`.
- F3. Toolchain (49 skills + 6 plugins) = Spring Boot + PostgreSQL + Redis + React/TS +
  Claude API + document processing + heavy security + Sentry.
- F4. `CLAUDE.md`: simple/maintainable, security-first, no unjustified deps, test-before-done.
- F5. Git: `main`, clean, initialization commits only.

**Source — Project 44 (verified against the PDF; detail in source doc):**
- F6. Course context: **24SDCS03A/R — SOA Programming and Microservices**, AY 2026-27.
  Project 44 = **Online Job Recruitment System**.
- F7. Confirmed product: recruitment platform — companies post jobs, candidates apply
  online; manages job listings, applications, hiring workflows incl. **shortlisting** +
  **status tracking**. _(Was assumption A1 — now confirmed.)_
- F8. Mandated services: **Job, Application, Recruitment** (+ **API Gateway, Auth, Eureka**).
- F9. Mandated tech: JWT (recruiters+candidates), API Gateway routing, Eureka, load
  balancing, inter-service comm (**Application → Job → Recruitment**), APIs (job posting /
  application submission / status tracking), unit+integration tests, deployment.
- F10. Source schema: Job / Application / Recruitment tables (see source doc).
- F11. Actors named by source: **Recruiter**, **Candidate**. No admin. `company_name` is a
  Job field, not a modeled entity. `resume_link` is a URL reference, not a stored file.
- F12. Source requires **APIs only** — **no UI** is specified. DBMS is **not** specified.

## ASSUMPTIONS (inferred, unconfirmed)

- A1. Concrete framework is **Spring Boot / Spring Cloud** (Eureka + API Gateway are
  Spring Cloud components; consistent with the toolchain). Near-certain, still our choice.
- A2. `company_name` on Job implies **multiple companies**, but no tenant isolation is specified.

## ~~Corrected assumption~~

- ❌ **AI is NOT a source requirement.** An earlier assumption treated AI as core to the
  product. The PDF contains **no AI requirement**. All AI features are now **proposals**
  (see source doc §7, E8/E9). Corrected 2026-09-14.

## OPEN QUESTIONS

**Resolved by Phase-2 approval (D4):** **OQ5** → UI **is in scope**; **OQ8** → all 11
enhancements approved; **OQ3** → résumé **upload + parsing added** (source `resume_link` kept as
the storage URL).

**Resolved by Phase-3 decisions (2026-09-15):** **OQ1** → status state machines (D5) ·
**OQ2 / OQ-R1** → user/org/roles model (D6) · **OQ-P3** → ADMIN in scope (D7) · **OQ-P1** → no
HIRING_MANAGER in MVP (D7) · **OQ-AI2** → AI out of MVP critical path (D8) · **OQ-B1/B2/B3** →
application/job rules (D10) · **OQ4 / OQ-S2** → OpenFeign sync inter-service (D11, ADR-0007) ·
**OQ-S1** → schema-per-service in one PostgreSQL for R1 (D12, ADR-0008) · **OQ6** → shortlisting
mechanics defined by the Application/Recruitment state machines (D5) · **OQ7 posture** →
provider-agnostic/containerized locked; **cloud target still open**.

**Still open (Phase-4 / deployment — not Phase-3 blockers):**
- 🟥 **OQ-AI1** — specific legal fairness metric + jurisdiction-specific compliance (policy set
  D9; the *metric* remains deferred).
- 🟥 **OQ-CI** — CI platform confirmation (GitHub Actions assumed; ADR-0019 pending).
- 🟥 **OQ-KEY** — JWT key-rotation cadence + per-environment key custody.
- 🟥 **OQ7 (target)** — production cloud/K8s target (posture decided D2; target deferred by owner).
- 🟥 **OQ9** — grading rubric/deadline: owner confirms **none exists**; source requirements are the
  **minimum mandatory acceptance baseline** (D13). Not to be invented.
- 🟥 **OQ-PKG** — final Maven group id / artifact coordinates (`com.smarthire` assumed), confirmed
  at bootstrap. _(Introduced in governance §3; trivial, no design impact.)_

## DECISIONS (locked)

- **D1 — Stack** _(2026-09-14, refined)_: Backend **Java + Spring Boot / Spring Cloud**
  microservices with **JUnit**; **PostgreSQL** relational store; **JWT** auth.
  - _Source-mandated_: microservices, Eureka, API Gateway, load balancing, inter-service
    comm, JWT, tests, deployment.
  - _Our additions (beyond source)_: **PostgreSQL**, **React/TS UI**, **Redis**,
    **Claude API/AI**, **Sentry** — now **APPROVED product enhancements (D4)**, phased per
    the Phase-2 MVP/R2/R3 split.
- **D4 — Product scope approved** _(2026-09-14)_: Source Project 44 requirements APPROVED.
  2026 enhancement scope APPROVED: React+TS web app · AI-assisted recruitment (Claude,
  human-in-the-loop) · résumé/document parsing · candidate–job matching + recruiter
  assistance · search & filtering · email/notifications · interview scheduling · analytics
  & dashboards · audit logging · PII/security/fairness safeguards · production
  observability/resilience/CI-CD/security automation. _Classification is preserved
  everywhere: SOURCE vs APPROVED ENHANCEMENT vs ASSUMPTION vs OPEN QUESTION._
- **D2 — Deployment posture** _(2026-09-14)_: provider-agnostic; containerize; choose
  target later (OQ7).
- **D3 — Architecture = MICROSERVICES** _(2026-09-14)_: **This supersedes the Phase-0
  provisional "modular monolith" recommendation.** _Rationale:_ the source is a
  microservices course and Project 44 explicitly mandates Eureka + API Gateway + load
  balancing + inter-service communication. A monolith would not satisfy the assignment.

### Phase-3 decisions _(2026-09-15; detail in `../architecture/phase-3-technical-architecture.md` + ADRs)_

- **D5 — Status state machines** _(resolves OQ1/OQ6)_: **Job** `DRAFT→OPEN→CLOSED` with
  controlled `OPEN↔CLOSED` reopen only when safe, no arbitrary jumps. **Application**
  `APPLIED→UNDER_REVIEW→SHORTLISTED→REJECTED`, plus `SHORTLISTED→HIRED` and
  `{APPLIED,UNDER_REVIEW,SHORTLISTED}→WITHDRAWN`; no invalid backward transitions.
  **Recruitment** keeps `interview_status` (`NOT_SCHEDULED→SCHEDULED→COMPLETED|NO_SHOW`) and
  `final_status` (`PENDING→HIRED|REJECTED`) **separate**, both transition-validated
  server-side. (Values are our choice on 🟦 source fields — labeled ✅, see architecture §26.)
- **D6 — Identity & org model** _(resolves OQ2/OQ-R1)_: `auth-service` owns User/Account; roles
  **CANDIDATE, RECRUITER, ADMIN**; recruiter linked to an **Organization** (minimal entity,
  `orgId` claim in JWT); candidate owns their applications/résumé. Authorization = **RBAC +
  object-level ownership** enforced per service. **No separate org/tenant microservice**
  (owner directive). `company_name` stays a Job field; `orgId` is the enforcement key. (ADR-0010)
- **D7 — Roles in MVP** _(resolves OQ-P3/OQ-P1)_: **ADMIN in scope** (platform-level operational —
  users, audit review, health; not a recruitment decision-maker). **No HIRING_MANAGER role** in
  the MVP — a single combined recruiter role.
- **D8 — AI out of the MVP critical path** _(resolves OQ-AI2)_: R1 core workflow must work fully
  **without AI**; R1 includes only non-decision-critical basic résumé parsing. All Claude-based
  matching/ranking, summaries, JD/recruiter assistance are **R2**, isolated in `ai-service`,
  **advisory + human-in-the-loop** (ADR-0015).
- **D9 — AI safety/fairness posture** _(partially resolves OQ-AI1)_: **never** intentionally use
  protected characteristics as model inputs; minimize/redact PII before prompts where practical;
  log model/prompt/version metadata for auditability **without** storing sensitive content;
  treat résumé text as untrusted (prompt-injection defenses); gate features behind an evaluation
  harness. **Specific legal fairness metric + jurisdiction compliance remain 🟥 OQ-AI1** (Phase 3/4).
- **D10 — Application/job business rules** _(resolves OQ-B1/B2/B3)_: **one active application per
  candidate per job**; candidate may **withdraw** while the state machine permits; recruiters may
  **close/reopen** jobs through controlled transitions only; **closed jobs cannot receive new
  applications**; duplicate job postings allowed unless a later requirement forbids it.
- **D11 — Inter-service communication** _(resolves OQ4/OQ-S2)_: **OpenFeign** (LB-integrated) for
  the source-mandated synchronous chain (Application→Job validate, Application→Recruitment
  advance), wrapped with **Resilience4j**; async side effects via outbox → Redis Streams. (ADR-0007)
- **D12 — Data strategy** _(resolves OQ-S1)_: **schema-per-service in one PostgreSQL** for R1
  (own schema + own DB user, no cross-schema FKs/joins, Flyway per service), with a clean path to
  database-per-service. (ADR-0008)
- **D13 — Acceptance baseline** _(acknowledges OQ9)_: no grading rubric/deadline exists in the
  source; **none is invented**. The Project 44 source requirements are the **minimum mandatory
  acceptance baseline**; enhancements are additive and phased.
- **D14 — Résumé storage is an implementation choice, not mandatory** _(2026-09-15, owner
  directive; refines OQ3/D4)_: the source only requires `resume_link` as a **URL reference**.
  MinIO/S3 (ADR-0013) is the enhancement's default backend but is **swappable behind the
  `document-service` storage interface** — a local filesystem/volume store, or the source-minimal
  external-URL-only path, are equally acceptable. **Do not treat object storage as a Project 44
  requirement**; pick the simplest compliant option at Inc 5. Storage port stays stable regardless.
