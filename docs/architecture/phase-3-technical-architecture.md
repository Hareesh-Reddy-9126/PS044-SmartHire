# Phase 3 — Technical Architecture — PS044-SmartHire (Online Job Recruitment System)

_Derived from `project-44-source-requirements.md`, `phase-2-product-model.md`, and the
product owner's Phase-3 decisions (2026-09-15). Foundational decisions are recorded once in
`adrs.md` (ADR-0001…ADR-0019) and **referenced** here, not repeated. **No application code is
written in Phase 3.** Nothing is built until this phase is approved._

## Classification legend (applied to every major decision)

- 🟦 **SOURCE REQUIREMENT** — explicit in the Project 44 PDF.
- 🟩 **APPROVED ENHANCEMENT** — owner-approved 2026 scope; not in the PDF.
- 🟨 **ASSUMPTION** — my inference, explicitly labeled; not silently invented.
- 🟥 **OPEN QUESTION** — needs an owner decision; deliberately left undecided.
- ✅ **DECISION** — locked in this phase (traces to an ADR and/or the requirements register).

> **Scope guardrail (owner directive):** do not invent requirements; do not add microservices
> merely because they are technically possible. The service set below is deliberately small and
> every non-source service carries an explicit justification (§2). Anti-over-engineering is a
> first-class constraint (ponytail discipline).

---

## 1. System architecture (overview) — ✅ (ADR-0001)

🟦→✅ Independently deployable Spring Boot **microservices** behind a single **API Gateway**,
with **Eureka** discovery and **client-side load balancing**. This is the source-mandated shape
(course = *SOA Programming and Microservices*), not a stylistic choice.

**Runtime topology (logical):**

```
                         ┌───────────────────────────────┐
        Browser (SPA 🟩) │        React + TS (Vite)       │
              │          └───────────────────────────────┘
              │ HTTPS (JWT: access in memory, refresh in httpOnly cookie)
              ▼
     ┌──────────────────┐      registers/discovers      ┌───────────────────┐
     │   API Gateway 🟦  │◄─────────────────────────────►│  Eureka (registry) │
     │ Spring Cloud GW  │        lb:// resolution        │       🟦           │
     └────────┬─────────┘                                └───────────────────┘
              │ routes (lb://SERVICE-ID), JWT validation, rate limit, CORS, correlation-id
   ┌──────────┼───────────────┬───────────────┬────────────────┬───────────────┐
   ▼          ▼               ▼               ▼                ▼               ▼
┌───────┐ ┌────────┐   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ auth  │ │  job   │   │ application  │ │ recruitment  │ │  document 🟩 │ │notification🟩│
│  🟦   │ │  🟦    │   │     🟦       │ │     🟦       │ │  (résumé)    │ │  (email)     │
└───┬───┘ └───┬────┘   └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────▲───────┘
    │         │               │  Feign         │                │               │
    │         │◄──────────────┘ (validate job) │                │               │
    │         │               └────────────────► (advance)      │               │
    │         │                                                  │               │ Redis Streams
    ▼         ▼               ▼                ▼                 ▼               │ (events)
 ┌─────────────────────────────────────────────────────────────────┐   ┌──────────────┐
 │  PostgreSQL — one instance, one schema per service (ADR-0008)     │   │    Redis     │
 │  auth_svc · job_svc · application_svc · recruitment_svc · doc_svc │   │ rate-limit/  │
 └─────────────────────────────────────────────────────────────────┘   │ revoke/events│
                          ▲                                              └──────────────┘
                          │ object keys                ┌──────────────┐
                          └────────────────────────────│ MinIO (S3) 🟩│  ai-service 🟩 (R2 only,
                                     résumé blobs       └──────────────┘  advisory, off critical path)
```

- **R1 services (built first):** `api-gateway` 🟦, `service-registry` 🟦, `auth-service` 🟦,
  `job-service` 🟦, `application-service` 🟦, `recruitment-service` 🟦, `document-service` 🟩,
  `notification-service` 🟩, plus a shared `common` library module (not a service).
- **R2 services (deferred):** `ai-service` 🟩, `config-server` 🟩.
- **R3 services (deferred):** `analytics-service` 🟩.
- 🟨 The SPA, MinIO, Redis, MailHog and PostgreSQL are infrastructure/enhancement components,
  not counted against the "keep services minimal" rule.

## 2. Service boundaries & responsibilities — ✅ (ADR-0001, Phase-2 §11)

| Service | Class | Owns (data) | Responsibility | Why a separate service |
|---|---|---|---|---|
| `api-gateway` | 🟦 | — | Edge routing, JWT validation, CORS, rate limit, headers, correlation | Source-mandated single entry point |
| `service-registry` (Eureka) | 🟦 | — | Service registration/discovery | Source-mandated |
| `auth-service` | 🟦 | **User/Account**, Organization (minimal), refresh tokens | Register/login, issue JWT (RS256), JWKS, role & org claims | Source-mandated (JWT); identity is its own bounded context |
| `job-service` | 🟦 | **Job** | Post/list/search/close/reopen jobs, job state machine | Source-named service |
| `application-service` | 🟦 | **Application** | Apply, list, status; **calls job-service** to validate job on apply | Source-named service |
| `recruitment-service` | 🟦 | **Recruitment**, Interview 🟩 | Shortlist, interview status, final status; **interview scheduling lives here** | Source-named service |
| `document-service` | 🟩 | **ResumeDocument** | Upload → store (S3) → basic parse; owns `resume_link` target | Distinct file-handling + parsing concern; heavier deps isolated |
| `notification-service` | 🟩 | **Notification** | Async email on status changes (event consumer) | External provider + retry/backoff; must not block core flow |
| `ai-service` (R2) | 🟩 | **MatchScore** | Claude matching/ranking, summaries, JD assist — advisory | Provider isolation + safety boundary + independent scaling |
| `config-server` (R2) | 🟩 | — | Centralized config | Deferred; env vars suffice for R1 |
| `analytics-service` (R3) | 🟩 | read models | Reporting/funnel | Deferred read side |
| `common` (library) | ✅ | — | DTOs, Problem-Details model, JWT/security utils, event schemas | Shared code, **not** a deployed service |

**Deliberately NOT separate services (🟨, anti-over-engineering):** no search service in R1
(search is a query in job/application); no scheduling service (interview scheduling is in
recruitment); audit is a **cross-cutting concern** (an event consumer writing to an audit store,
§19), not its own service in R1.

**OQ2 / user & org modeling — ✅ (D6):** `auth-service` owns **User/Account** (id, email,
passwordHash, role, orgId?). Roles **CANDIDATE, RECRUITER, ADMIN**. A recruiter is linked to an
**Organization**; a candidate owns their applications/résumé; ADMIN is platform-level operational
(not a recruitment actor). Organization modeling is kept **minimal** — a lightweight entity in
auth-service carrying `orgId` into the JWT — **no separate org/tenant microservice** (owner
directive). `company_name` remains a field on Job (🟦); `orgId` is the enforcement key (§6).

## 3. API Gateway design — ✅ (ADR-0004)

🟦→✅ **Spring Cloud Gateway** (reactive) is the single entry point.
- **Routing:** `lb://SERVICE-ID` predicates per path prefix (`/api/auth/**`, `/api/jobs/**`,
  `/api/applications/**`, `/api/recruitment/**`, `/api/documents/**`), resolved via Eureka.
- **Edge security:** validates the JWT (signature via `auth-service` JWKS, exp, issuer,
  audience) and rejects unauthenticated calls to protected routes **before** they reach a
  service; propagates identity via a verified context. Coarse role gating at the edge; **fine
  authorization stays in each service** (§6) — the gateway is not the sole authz point.
- **Cross-cutting:** CORS (allow-list the SPA origin), security headers (HSTS, X-Content-Type,
  frame-deny, referrer-policy), request/response size limits, **rate limiting** (Redis,
  ADR-0011), and a generated/propagated **`X-Correlation-Id`** for tracing (§20).
- 🟨 Public routes (login, register, public job browse) are explicitly allow-listed; everything
  else is deny-by-default.

## 4. Service discovery (Eureka) design — ✅ (ADR-0005)

🟦→✅ A `service-registry` running **Netflix Eureka Server**; every service is a Eureka client
registering by `spring.application.name` (= SERVICE-ID). Gateway and Feign clients resolve
`lb://SERVICE-ID`. R1 runs a **single registry instance** locally (🟨 self-preservation tuned
for dev; a peer-aware HA pair is an R3 concern, deferred — not needed for the grade). No
hard-coded host:port anywhere (verified by AC-4).

## 5. Authentication & JWT strategy — ✅ (ADR-0009)

🟦→✅ Stateless JWT, **RS256**, `auth-service` is the sole issuer.
- **Tokens:** short-lived **access token** (JWT, ~15 min 🟨) + longer-lived **refresh token**
  (opaque or JWT, rotated). Claims: `sub`, `roles`, `orgId`, `iss`, `aud`, `exp`, `iat`, `jti`.
- **Verification:** resource services are stateless **OAuth2 resource servers** validating the
  signature offline via the published **JWKS**; no shared secret, no per-request call to auth.
- **Refresh & revocation:** refresh rotation with reuse-detection; a **Redis denylist** keyed by
  `jti` supports logout/revocation (ADR-0011). Access token in SPA **memory**; refresh token in
  an **httpOnly, Secure, SameSite** cookie (ADR-0014) — never in localStorage.
- **Password storage:** BCrypt/Argon2 hashing (🟨 Argon2id default; confirm at bootstrap).
- 🟥 **OQ-KEY:** key rotation cadence + where private keys live in each environment (§24) is a
  deployment detail deferred to Phase 4 governance.

## 6. Authorization & RBAC model — ✅ (ADR-0010, D6/D7)

🟦(roles)/🟩(org)→✅ **RBAC + object-level ownership**, enforced **in each service** via Spring
Security method security (`@PreAuthorize`), not only at the gateway.
- **Roles:** CANDIDATE, RECRUITER, ADMIN. **No HIRING_MANAGER role in the MVP** (D7, owner
  directive) — a single combined recruiter role.
- **Object-level rules:** a RECRUITER may act only on jobs/applications whose `orgId` matches the
  token's `orgId`; a CANDIDATE only on their own applications/résumé (`sub` match); ADMIN is
  platform-level operational (user management, audit review, health) and **not** a recruitment
  decision-maker.
- **Enforcement pattern:** ownership checks run against the resource's stored `orgId`/`candidateId`
  at the service layer (defense in depth behind the gateway's coarse gate).

## 7. Data ownership: DB-per-service vs schema-per-service — ✅ (ADR-0008, resolves OQ-S1)

✅ **Schema-per-service in one PostgreSQL for R1**, with a clean path to database-per-service.
- Each service owns its **own schema** and **own DB user**; **no cross-schema FKs or joins**.
- Cross-service references store **remote IDs only** (e.g. Application holds `job_id`,
  `candidate_id` as plain columns) validated via API/events, never via a database join.
- **Flyway** migrations **per service** (each service versions its own schema).
- **Trade-off (documented):** a shared instance is a blast-radius/scaling seam. Because there are
  no cross-schema joins, promoting any schema to its own database is a connection-string change,
  not a redesign. Promote when a service needs independent scaling (R3).

| Schema | Service | Core tables (🟦 source tables in bold) |
|---|---|---|
| `auth_svc` | auth-service | users, organizations 🟩, refresh_tokens |
| `job_svc` | job-service | **jobs** |
| `application_svc` | application-service | **applications**, outbox |
| `recruitment_svc` | recruitment-service | **recruitments**, interviews 🟩, outbox |
| `document_svc` | document-service | resume_documents 🟩 |

## 8. Inter-service communication — ✅ (ADR-0007, resolves OQ4/OQ-S2)

🟦→✅ Two complementary channels:
- **Synchronous (source-mandated chain):** declarative **OpenFeign** clients over
  `lb://SERVICE-ID`, wrapped with **Resilience4j** (§25). Used only for genuine request-time
  needs — the mandated **Application → Job** validation on apply (does the job exist and is it
  OPEN?), and **Application → Recruitment** advancement on shortlist. Kept read-only where
  possible to avoid distributed writes.
- **Asynchronous (enhancement, decoupling):** domain events via **transactional outbox → Redis
  Streams** (ADR-0012) for notifications and audit — these must never block or couple to the core
  flow.
- 🟨 The source phrase "Application → Job → Recruitment" is modeled as: Application validates
  against Job (sync), and the recruitment record advances from an Application (sync on the
  shortlist action); status-change side effects propagate async.

## 9. Load balancing — ✅ (ADR-0006)

🟦→✅ **Spring Cloud LoadBalancer** (client-side; Ribbon is EOL). Gateway routes and Feign
clients resolve `lb://SERVICE-ID` across all Eureka-registered instances. **Verification:** run
**≥2 instances** of `job-service`; application-service's validate calls distribute across them
(AC-4). Default round-robin (🟨; sufficient for R1).

## 10. Redis usage — ✅ (ADR-0011, ADR-0012)

🟩→✅ Redis is used **only where it earns its place** in R1:
- **Rate limiting** at the gateway (request-token bucket).
- **Token revocation / refresh denylist** keyed by `jti`.
- **Event transport:** **Redis Streams** with consumer groups for domain events (§12, §19).
- **Opportunistic caching** of hot reads (job listings/reference data) — added only if a real
  hotspot appears, not preemptively (ponytail).

## 11. Résumé storage & document handling — ✅ (ADR-0013, resolves OQ3)

🟩→✅ `document-service` stores résumé files in **S3-compatible object storage** (**MinIO**
locally; same API on AWS/GCP/Azure). `resume_link` (🟦 source field) holds the **object
key/URL** — files never live in the database.
- **Upload validation:** MIME allow-list (PDF/DOCX), size cap, filename sanitization,
  antivirus-scan hook (🟨 stub in R1).
- **Access:** short-lived **pre-signed URLs**; no public buckets.
- **Parsing:** R1 does **basic field extraction only** (non-decision-critical, D8); richer
  parsing is R2.

## 12. Notification architecture — ✅ (ADR-0012)

🟩→✅ `notification-service` is an **event consumer** (Redis Streams consumer group). Status
changes in application/recruitment emit events via the outbox; notification-service sends email
(**MailHog** locally, provider-agnostic SMTP/API interface). Retry with backoff; failures don't
affect the emitting service. Templates per event type. 🟨 Email only in R1; other channels later.

## 13. AI service (R2) — ✅ (ADR-0015, D8/D9)

🟩→✅ AI is **isolated in `ai-service`**, **R2 only**, **advisory**, and **off the mandatory
critical path** (owner directive — the core recruitment workflow must work fully without AI).
- **Capabilities (R2):** candidate–job matching/ranking with rationale, candidate summaries,
  JD/recruiter drafting assistance. Integrates the **Claude API**.
- **Hard boundaries:** AI **never** shortlists/rejects/hires autonomously and never hides
  candidates; every state change requires an explicit human action (BR-4). Outputs carry
  rationale + model/prompt-version metadata and are **schema-validated**.
- **Safety/fairness (D9):** **protected characteristics are never intentionally used as model
  inputs**; PII is minimized/redacted before prompts where practical; résumé text is treated as
  **untrusted input** (prompt-injection defenses); model/prompt/version metadata is logged for
  auditability **without** storing sensitive content in the clear; features gate through an
  **evaluation harness** before release.
- 🟥 **OQ-AI1 (still open):** the specific **legal fairness metric** and jurisdiction-specific
  compliance are deferred to a Phase 3/4 compliance decision — no metric is claimed yet.
- **R1 stance:** only non-decision-critical basic résumé parsing (in document-service) — no
  Claude dependency in the mandatory workflow.

## 14. Frontend architecture — ✅ (ADR-0014, resolves OQ10/OQ5)

🟩→✅ **Vite + React + TypeScript SPA** (not Next.js — no SSR/SEO need, provider-agnostic goal).
- **Server state:** React Query; **routing:** React Router; **validation:** Zod (mirrors
  server-side Bean Validation, §17).
- **Auth:** access token in memory, refresh token in httpOnly Secure cookie; silent refresh.
- **Deploy:** static assets behind nginx/CDN; talks only to the gateway.
- 🟦 Note: source requires **APIs only** — the UI is an approved enhancement (E7), so the API
  layer is designed to be fully usable and gradable **without** the SPA.

## 15. API contract & versioning strategy — ✅

✅ **REST/JSON**, resource-oriented, documented with **OpenAPI 3** per service (springdoc;
enhancement E5 🟩). 
- **Versioning:** URI prefix **`/api/v1/...`** from day one (🟨 default; cheap insurance,
  cheapest to change now).
- **Contracts:** the `common` module holds shared DTOs; **Spring Cloud Contract** produces
  consumer-driven contract tests for the Feign boundaries (§21) so a provider change that breaks
  a consumer fails CI.
- **Conventions:** plural nouns, standard verbs, pagination (`page`/`size`) and filtering on list
  endpoints, RFC 9457 errors (§16).

## 16. Error handling — ✅ (ADR-0016)

✅ **RFC 9457 Problem Details** (`application/problem+json`) via Spring `ProblemDetail` +
`@RestControllerAdvice` in every service (shared base in `common`). Consistent
`type/title/status/detail/instance` + `correlationId`; **no stack traces, SQL, or internals
leaked** to clients. Validation failures (§17) map to a 400 problem with field-level details.

## 17. Input validation strategy — ✅

✅ **Bean Validation (Jakarta `@Valid`)** on all inbound DTOs at every service boundary
(defense in depth — the gateway is not trusted to validate payloads). Constraint annotations +
custom validators for domain rules (e.g. status-transition legality, §26). The SPA mirrors rules
with **Zod** for UX, but the **server is authoritative**. Untrusted file uploads validated in
document-service (§11); résumé text treated as untrusted before any AI use (§13).

## 18. Security architecture (consolidated) — ✅

🟦/🟩→✅ Security-first per `CLAUDE.md`. Layers:
- **Edge:** TLS, JWT validation, CORS allow-list, security headers, rate limiting, size limits.
- **Identity:** RS256 JWT + JWKS, refresh rotation + reuse detection, Redis revocation, strong
  password hashing (§5).
- **Authorization:** RBAC + object-level ownership in each service (§6).
- **Input:** Bean Validation everywhere; parameterized queries via JPA; output encoding in SPA.
- **Data:** secrets never in code/repo (`.env*` git-ignored, §24); PII minimized before AI;
  pre-signed URLs for files; least-privilege DB users per schema.
- **Supply chain:** dependency + secret + SAST scanning in CI (§23, ADR-0019).
- **Auditability:** sensitive actions logged (§19).
- Full **STRIDE** analysis in §31.

## 19. Audit logging — ✅ (Phase-2 BR-6)

🟩→✅ Sensitive actions (status changes, role/user management, data access to applications, any
future AI-assisted recommendation) emit an **audit event** via the outbox → Redis Streams; an
audit consumer persists them to an append-only store (a dedicated schema/table in R1;
promotable). Audit is a **cross-cutting concern**, not a separate microservice in R1. Records:
actor, action, entity, before/after (non-sensitive), timestamp, correlationId. 🟨 Retention policy
is a Phase-4 governance item.

## 20. Observability — ✅ (enhancement E3)

🟩→✅ Three pillars, phased:
- **Errors:** **Sentry** in every service and the SPA (R1).
- **Logs:** structured JSON logs with the propagated `correlationId` (R1).
- **Metrics:** **Micrometer** → Actuator/Prometheus endpoints (R1 basic).
- **Tracing:** distributed tracing (Micrometer Tracing/OpenTelemetry) — **R2/R3** (deferred; not
  needed for the grade, cheap to add once tracing IDs already flow).
- **Health:** Spring Actuator `/health` per service, consumed by Compose healthchecks (§22).

## 21. Testing strategy — ✅ (ADR-0002; SOURCE R11/C8)

🟦→✅ Source mandates **unit + integration testing**. Pyramid:
- **Unit:** JUnit 5 + Mockito (domain logic, state machines §26, authz rules).
- **Integration:** **Testcontainers** (real PostgreSQL + Redis) per service; Spring Boot slice
  tests.
- **Contract:** **Spring Cloud Contract** on the Feign boundaries (Application↔Job,
  Application↔Recruitment) — verifies the source-mandated inter-service chain.
- **E2E (🟩):** Playwright against the Compose stack for core journeys (R1 thin).
- **Gate:** all of the above run in CI (§23); `docker compose up` health-verified (AC-8).
- 🟨 Coverage target (e.g. ≥80% on domain modules) set in Phase-4 governance, not invented here.

## 22. Docker & local development — ✅ (ADR-0017; SOURCE R12/C9)

🟦→✅ Every service ships a **multi-stage Docker image** (slim JRE, non-root user).
`docker-compose.yml` brings up the **entire system locally**: gateway, eureka, all R1 services,
postgres, redis, minio, mailhog, frontend — wired by Compose service names with **healthchecks**
and dependency ordering. `docker compose up` = full local system (AC-8). Hot-reload/dev profiles
via a `compose.override.yml` (🟨).

## 23. CI/CD pipeline — Proposed (ADR-0019, 🟥 OQ-CI)

🟨🟥 CI design (platform pending confirmation): on each PR → **build** → **unit + integration
(Testcontainers)** → **contract tests** → **dependency scan + SAST + secret scanning** → **build
images**. CD (image publish/deploy) deferred with the cloud target (§27).
- 🟥 **OQ-CI:** confirm the CI platform (GitHub Actions assumed since the remote is Git/GitHub,
  but unconfirmed). Wiring waits on this.

## 24. Secrets & configuration management — ✅

✅ **Twelve-factor**: all config via environment variables; **no secrets in code or the repo**
(`.gitignore` blocks `.env*`, verified F2). Local dev uses a git-ignored `.env`; a committed
`.env.example` documents **keys only, never values**. RS256 **private key** injected per
environment (never committed); public JWKS served by auth-service.
- **R1:** env vars + `.env` (sufficient, ponytail).
- **R2:** **Spring Cloud Config** server (E1) once multi-environment config justifies it.
- 🟥 A managed secrets store (Vault/cloud KMS) is tied to the deployment-target decision (§27).

## 25. Resilience & fault tolerance — ✅ (enhancement E2)

🟩→✅ **Resilience4j** on the synchronous Feign boundaries: **timeouts** (always), **retries**
(idempotent reads only), **circuit breakers**, and sensible **fallbacks** (e.g. apply fails
closed with a clear Problem Detail if job-service is unreachable — never silently accept an
application to an unvalidated job). Async consumers (§12) get retry + dead-letter handling.
- 🟨 R1 ships timeouts + one circuit breaker on the apply→job call; broader policies in R2.

## 26. Data consistency & transaction model — ✅ (ADR-0018, D5 state machines)

✅ Each service is its **own ACID boundary** (local `@Transactional`). **No distributed
transactions, no 2PC, no saga in R1** — the apply flow validates Job **read-only** then writes
Application locally, so no cross-service write transaction exists. Cross-service consistency is
**eventual** via the **transactional outbox** (event row committed in the same local tx) → Redis
Streams (ADR-0012).

**State machines (🟦 fields, values ✅ D5 per owner directive; transitions validated server-side):**

- **Job.status** — `DRAFT → OPEN → CLOSED`; **OPEN ↔ CLOSED** reopening allowed only when safe;
  no arbitrary jumps.
  ```
  DRAFT ──▶ OPEN ──▶ CLOSED
              ▲────────┘   (reopen, controlled)
  ```
- **Application.status** — `APPLIED → UNDER_REVIEW → SHORTLISTED → REJECTED`; **SHORTLISTED →
  HIRED**; **{APPLIED, UNDER_REVIEW, SHORTLISTED} → WITHDRAWN** (candidate-initiated); **no
  invalid backward transitions**.
  ```
  APPLIED ─▶ UNDER_REVIEW ─▶ SHORTLISTED ─▶ REJECTED
     │            │              │
     └────────────┴──────────────┴──▶ WITHDRAWN        SHORTLISTED ─▶ HIRED
  ```
- **Recruitment.interview_status** — `NOT_SCHEDULED → SCHEDULED → COMPLETED | NO_SHOW`
  (kept **separate** from final status, per directive).
- **Recruitment.final_status** — `PENDING → HIRED | REJECTED` (terminal), validated transitions.

**Business rules locked (D10):** one **active** application per candidate per job (BR-8 ✅);
candidate may **withdraw** while the state machine permits (BR, ✅); recruiters may
**close/reopen** jobs through controlled transitions only (✅); **closed jobs cannot receive new
applications** (BR-9 ✅); duplicate job postings allowed unless a later requirement forbids it
(🟨). Illegal transitions are rejected with a 409/422 Problem Detail (§16).

## 27. Deployment topology & target — ✅ posture / 🟥 target (ADR-0017, D2)

🟩/OQ7→✅ **Container-first, provider-agnostic** (owner directive: do not lock to
AWS/GCP/Azure yet). R1 target = **Docker Compose** full-system local (satisfies "Deploy",
SOURCE R12/C9). Cloud/Kubernetes manifests are a later, additive step (the S3/SMTP/config
abstractions already keep us portable).
- 🟥 **OQ7 (still open):** production cloud/K8s target — deferred by owner; no lock-in incurred.

## 28. Environment strategy — ✅

✅ Environments: **local (Compose)**, **CI (ephemeral Testcontainers)**, and a future
**staging/prod** (tied to §27). Config differs only by environment variables (§24); the same
images promote across environments (build once, deploy many). 🟨 Spring profiles: `local`, `ci`,
`prod`.

## 29. Dependency choices with justification — ✅

Each dependency traces to a requirement or an approved enhancement; **no dependency added for
what a few lines can do** (`CLAUDE.md`).

| Dependency | Class | Justification |
|---|---|---|
| Spring Boot 3.3+ / Spring Cloud | 🟦→✅ | Eureka + Gateway are Spring Cloud; toolchain (ADR-0002) |
| Java 21 LTS | 🟨→✅ | Supported LTS, virtual threads (ADR-0002) |
| Spring Cloud Gateway | 🟦→✅ | Source API-Gateway requirement (ADR-0004) |
| Netflix Eureka | 🟦→✅ | Source discovery requirement (ADR-0005) |
| Spring Cloud LoadBalancer | 🟦→✅ | Source load-balancing requirement; Ribbon EOL (ADR-0006) |
| OpenFeign | 🟦→✅ | Source inter-service chain (ADR-0007) |
| Resilience4j | 🟩→✅ | Fault tolerance on sync calls (§25) |
| Spring Security + OAuth2 Resource Server | 🟦→✅ | JWT auth requirement (§5–6) |
| PostgreSQL + JPA/Hibernate + Flyway | 🟦(relational)/🟨(engine)→✅ | Source relational model; Postgres chosen; versioned migrations (ADR-0008) |
| Redis (Lettuce) | 🟩→✅ | Rate limit, revocation, event streams (ADR-0011/0012) |
| AWS S3 SDK / MinIO | 🟩→✅ | Provider-agnostic résumé storage (ADR-0013) |
| React + TypeScript + Vite, React Query, React Router, Zod | 🟩→✅ | SPA (ADR-0014) |
| springdoc-openapi | 🟩→✅ | API contracts (§15, E5) |
| JUnit 5, Mockito, Testcontainers, Spring Cloud Contract | 🟦→✅ | Source test requirement (§21) |
| Playwright | 🟩→✅ | E2E for core journeys (§21) |
| Sentry, Micrometer | 🟩→✅ | Observability (§20, E3) |
| Anthropic Claude SDK | 🟩→✅ (R2) | AI service only (§13) |

## 30. Architecture Decision Records — ✅

See **`docs/architecture/adrs.md`** (ADR-0001…ADR-0019). Each ADR carries context, decision,
consequences, and a class tag. This document references them by number rather than repeating.

## 31. Threat model (STRIDE) — ✅

Assets: user credentials & JWTs, résumé files (PII), application/recruitment data, inter-service
calls, the Claude API key (R2). Entry points: SPA→gateway, gateway→services, service→service
(Feign), service→PostgreSQL/Redis/MinIO, service→Claude (R2).

| STRIDE | Threat (example) | Mitigation | Refs |
|---|---|---|---|
| **S**poofing | Forged/stolen JWT; impersonating a service | RS256 signatures + JWKS verify; short-lived access tokens; refresh rotation + reuse detection; Redis revocation; services register in Eureka on a trusted network | §5, §6, ADR-0009/0011 |
| **T**ampering | Modified request payload; illegal status jump; altered résumé object | Bean Validation everywhere; server-authoritative state machines; TLS in transit; object integrity via storage; least-privilege DB users; no cross-schema writes | §17, §26, §7 |
| **R**epudiation | Recruiter denies rejecting a candidate | Append-only audit log with actor + correlationId on all sensitive actions | §19 |
| **I**nformation disclosure | PII leak in errors/logs; résumé exposure; PII to AI | RFC 9457 (no internals leaked); PII minimized/redacted before AI; pre-signed short-lived URLs, no public buckets; secrets never in repo; structured logs avoid sensitive fields | §11, §13, §16, §24 |
| **D**enial of service | Credential stuffing; upload flooding; expensive AI calls | Gateway rate limiting; upload size/MIME caps; Resilience4j timeouts/circuit breakers; AI off critical path + gated | §3, §10, §11, §25 |
| **E**levation of privilege | Candidate acts on another's data; recruiter crosses orgs; role escalation | RBAC + object-level ownership (`sub`/`orgId`) enforced in each service; deny-by-default routes; ADMIN scoped to ops, not recruitment decisions | §6, §3 |

🟥 Residual/open: legal fairness metric (OQ-AI1), key-rotation policy (OQ-KEY), managed secrets
store & cloud threat surface (tied to §27/OQ7) — carried into Phase 4 governance.

## 32. Performance & scalability — ✅ posture

🟨→✅ R1 targets **correctness and demonstrability**, not throughput. Scalability is
**structural**: stateless services scale horizontally behind Eureka + LoadBalancer (proven by
the ≥2-instance LB test, AC-4); schema-per-service can split to database-per-service without
redesign (§7); async events absorb spikes off the critical path (§12); Redis caching is
available for read hotspots (§10). **No premature optimization** — concrete SLOs/load testing are
an R3 concern (ponytail). 🟥 No performance rubric exists in the source (OQ9); none invented.

## 33. Phase-3 → build roadmap — ✅

Vertical-slice increments (from source doc §8, now with Phase-3 decisions folded in). **No code
until Phase 3 is approved.**

- **Inc 0 — Skeleton:** parent Maven build, `common`, Eureka server, gateway, one `job-service`
  endpoint registered + reachable through the gateway; one Testcontainers integration test.
  _Acceptance: request routes gateway → Eureka → job._
- **Inc 1 — Auth + JWT:** auth-service (User/Org minimal), RS256 issue/verify, JWKS, RBAC, gateway
  edge validation. _Acceptance: protected routes reject unauthenticated calls; roles honored (AC-1)._
- **Inc 2 — Job (full):** posting/listing/search/close/reopen + Job state machine + tests.
  _Acceptance: AC-2._
- **Inc 3 — Application:** apply/submit/status, `resume_link`, **Feign → job validate**,
  **load-balanced (≥2 job instances)**, Application state machine, apply-once rule.
  _Acceptance: AC-3, AC-4._
- **Inc 4 — Recruitment:** shortlist, interview & final status (separate), status tracking,
  Application → Recruitment link. _Acceptance: AC-5._
- **Inc 5 — Enhancements (R1 thin):** document-service (upload→store→basic parse),
  notification-service (email on status change), SPA for core flows, audit writes, OpenAPI,
  Sentry + structured logging. _Acceptance: AC-6, AC-7._
- **Inc 6 — Test + deploy hardening:** contract tests, full Testcontainers coverage, Docker
  Compose full-system, CI wiring (after OQ-CI). _Acceptance: AC-8._
- **R2/R3 (deferred):** ai-service + eval harness, richer parsing, interview scheduling UI,
  resilience expansion, config server; then analytics, semantic search, fairness monitoring,
  tracing dashboards, security automation.

---

## Traceability matrix — every mandatory Project 44 requirement → architecture component

_Verification required before finalizing Phase 3. Every 🟦 source requirement maps to a concrete
component, section, and (where applicable) ADR + acceptance criterion. **All mandatory
requirements are covered.**_

| # | Mandatory source requirement | Architectural component(s) | Section | ADR | Acceptance |
|---|---|---|---|---|---|
| C1 | Microservices architecture | Full service topology (auth/job/application/recruitment + gateway + registry) | §1, §2 | 0001 | AC-8 |
| C2 / R7 | API Gateway (request routing) | `api-gateway` (Spring Cloud Gateway) | §3 | 0004 | AC-1 |
| C3 / R8 | Eureka service discovery | `service-registry` + all services as clients | §4 | 0005 | AC-4 |
| C4 / R9 | Load balancing | Spring Cloud LoadBalancer (`lb://`), ≥2 instances | §9 | 0006 | AC-4 |
| C5 / R10 | Inter-service comm (Application→Job→Recruitment) | OpenFeign clients + Resilience4j | §8 | 0007 | AC-3, AC-4 |
| C6 / R6 | JWT auth (recruiters + candidates) | `auth-service` issuer + gateway validation + resource servers | §5, §6 | 0009, 0010 | AC-1 |
| C7 | Relational model (Job/Application/Recruitment) | Schema-per-service PostgreSQL, JPA entities, Flyway | §7, §26 | 0008 | AC-2, AC-3, AC-5 |
| C8 / R11 | Unit + integration testing | JUnit 5, Mockito, Testcontainers, Spring Cloud Contract | §21 | 0002 | AC-8 |
| C9 / R12 | Deployment | Multi-stage Docker images + docker-compose full system | §22, §27 | 0017 | AC-8 |
| R1 | Post jobs / manage listings | `job-service` (post/list/search/close APIs) | §2, §33 Inc2 | — | AC-2 |
| R2 | Candidates apply online / manage applications | `application-service` (apply/list/status APIs) | §2, §33 Inc3 | — | AC-3 |
| R3 | Hiring workflows incl. shortlisting | `recruitment-service` (shortlist, interview/final status) | §2, §26, §33 Inc4 | — | AC-5 |
| R4 | Status tracking | application + recruitment read endpoints; state machines | §26 | — | AC-3, AC-5 |
| R5 | APIs for posting / submission / status | REST APIs per service, OpenAPI, `/api/v1` | §15 | 0016 | AC-2, AC-3 |
| — | Actors: Recruiter, Candidate (JWT) | RBAC roles CANDIDATE/RECRUITER (+ADMIN ops) | §6 | 0010 | AC-1 |
| — | Job table (job_id, company_name, role, location, status) | `job_svc.jobs` + Job state machine | §7, §26 | 0008 | AC-2 |
| — | Application table (application_id, job_id, candidate_id, resume_link, status) | `application_svc.applications` + state machine | §7, §26 | 0008 | AC-3 |
| — | Recruitment table (recruitment_id, application_id, interview_status, final_status) | `recruitment_svc.recruitments` + two state machines | §7, §26 | 0008 | AC-5 |

**Open items carried forward (not blockers to Phase-3 approval):** OQ-AI1 legal fairness metric ·
OQ-CI CI platform · OQ-KEY key rotation · OQ7 cloud target · OQ9 grading rubric (owner: none
exists; source is the minimum baseline).
