# Project 44 — Source Requirements (Authoritative)

_Source of truth: `Project Details.pdf`, course **24SDCS03A/R — SOA Programming and
Microservices**, AY 2026-27 (Odd Semester), **Project 44: Online Job Recruitment System**._
_Extracted 2026-09-14. Everything in sections 1–6 is traceable to the PDF. Section 7 is
explicitly **proposed, not required**._

> **Verbatim scenario:** "A recruitment platform where companies post job openings and
> candidates apply online. The system manages job listings, applications, and hiring
> workflows, including shortlisting and status tracking."

---

## 1. Source-derived requirements

**Functional (from scenario + "Tasks to be Done" + "APIs"):**
- R1. Companies/recruiters **post job openings**; system **manages job listings**.
- R2. Candidates **apply online** to jobs; system **manages applications**.
- R3. System manages **hiring/recruitment workflows**, including **shortlisting**.
- R4. System performs **status tracking** of applications and recruitment.
- R5. Provide **APIs for**: job posting, application submission, status tracking.

**Mandated capabilities (from "Tasks to be Done"):**
- R6. **JWT authentication** for recruiters and candidates.
- R7. **API Gateway** for request routing.
- R8. **Eureka** service registration/discovery.
- R9. **Load balancing** across services.
- R10. **Inter-service communication** in the chain **Application → Job → Recruitment**.
- R11. **Unit & integration testing**.
- R12. **Deploy** the system.

## 2. Source-derived actors / roles

- **Recruiter** (acting for a company) — authenticated via JWT; posts jobs, runs hiring.
- **Candidate** — authenticated via JWT; applies to jobs, tracks status.

_No "admin" or other role is named in the source. `company_name` is a **field on Job**,
not a modeled Company/User entity. A user/account store is implied by JWT + `candidate_id`
but its schema is **not given** in the source._

## 3. Source-derived workflows

1. **Authenticate** (recruiter or candidate) → JWT issued via Auth, routed through the API Gateway.
2. **Post job** (recruiter) → Job Service creates a Job (`status`).
3. **Apply to job** (candidate) → Application Service creates an Application referencing
   `job_id`, `candidate_id`, `resume_link`, `status`; validates against Job Service
   (the Application → Job link).
4. **Recruitment / shortlisting** (recruiter) → Recruitment Service records
   `interview_status` and `final_status` per application; supports shortlisting.
5. **Track status** (both roles) → read application & recruitment status.

_Inter-service dependency as stated: **Application → Job → Recruitment**._

## 4. Source-derived domain concepts

| Entity | Fields (verbatim) | Notes |
|---|---|---|
| **Job** | `job_id` (PK), `company_name`, `role`, `location`, `status` | Owned by Job Service. |
| **Application** | `application_id` (PK), `job_id`, `candidate_id`, `resume_link`, `status` | Owned by Application Service; `job_id` → Job, `candidate_id` → user. `resume_link` is a **URL/reference**, not a stored file. |
| **Recruitment** | `recruitment_id` (PK), `application_id`, `interview_status`, `final_status` | Owned by Recruitment Service; `application_id` → Application. |

_Status fields (`status`, `interview_status`, `final_status`) have **no enumerated values**
in the source — see open questions._

## 5. Mandatory technical constraints (from the PDF)

- **C1. Microservices architecture** — the course is *SOA Programming and Microservices*;
  Project 44 names the services **Job**, **Application**, **Recruitment** (+ **Auth**).
- **C2. API Gateway** for request routing (single entry point).
- **C3. Eureka service discovery** — services self-register.
- **C4. Load balancing** across service instances.
- **C5. Inter-service communication** (Application → Job → Recruitment).
- **C6. JWT authentication** for recruiters and candidates.
- **C7. Relational data model** matching the Job / Application / Recruitment tables above.
- **C8. Unit + integration tests.**
- **C9. Deployment** of the running system.

_Strongly implied but not literally stated: **Java + Spring Boot / Spring Cloud** (Eureka
and the API Gateway are Spring Cloud components). **DBMS is not specified**; **no UI is
required** — the source asks only for APIs._

## 6. Ambiguities / open questions (must be resolved before/within design)

- OQ1. Allowed values for `status`, `interview_status`, `final_status` (state machines).
- OQ2. User/account & company modeling: keep `company_name` as a string, or introduce
  Company / User entities? What is the Auth service's user schema (not in source)?
- OQ3. `resume_link` — external URL only, or do we add upload + storage (enhancement)?
- OQ4. Inter-service comm mechanism (OpenFeign vs WebClient) and sync vs async; exact
  meaning of the Application → Job → Recruitment chain.
- OQ5. Is a **frontend UI** in scope/graded, or **APIs-only**? (Determines whether the
  React frontend is core or an enhancement.)
- OQ6. Shortlisting mechanics: who shortlists, on what criteria, and state transitions.
- OQ7. Deployment target for "Deploy system": Docker Compose vs cloud vs Kubernetes.
- OQ8. Which Section-7 enhancements are approved into scope.
- OQ9. Grading rubric / deadline — not present in the Project 44 section; is there one?

---

## 7. 2026 Production-Grade Enhancements — Proposed, Not Required by Source

> **STATUS: PROPOSALS ONLY. None of these are in the PDF. Do not build any of them
> until explicitly approved by the product owner. Each is opt-in.**

**Platform / microservices maturity (recommended for any real microservice system):**
- E1. Centralized config (Spring Cloud Config), secrets management.
- E2. Resilience (Resilience4j: circuit breakers, retries, timeouts, bulkheads).
- E3. Observability: **Sentry** errors, structured logging, metrics, **distributed tracing** (Micrometer/OpenTelemetry).
- E4. Containerization (Docker + Compose) and **CI/CD** with security scanning.
- E5. **OpenAPI/Swagger** contracts per service.
- E6. **Redis** for caching / rate limiting / token or session support.

**Product features:**
- E7. **Web UI** (React + TypeScript) for recruiters & candidates (source = APIs only).
- E8. **AI-assisted recruitment** (Claude API): candidate–job matching/ranking, JD
  drafting, screening summaries — **human-in-the-loop**, with evaluation + guardrails.
- E9. **Resume parsing** (PDF/DOCX) into structured profile data (source uses `resume_link`).
- E10. **Notifications** (email) on status changes / interview invites.
- E11. **Interview scheduling**.
- E12. **Search & filtering** for jobs/candidates (optionally semantic/vector search).
- E13. **Analytics/reporting** (pipeline funnel, time-to-hire).
- E14. **Audit logging** of recruiter actions.
- E15. **Fairness/bias controls** + PII protection/compliance for any AI decisioning.

---

## 8. Revised roadmap

See `requirements-register.md` DECISIONS for the architecture reversal (microservices,
not the Phase-0 modular monolith). Increments are ordered as a **thin vertical slice
first**, then enhancements only after approval:

- **Inc 0 — Microservices skeleton:** parent build, **Eureka server**, **API Gateway**,
  one **Job Service** with a single endpoint registered + reachable through the gateway;
  Testcontainers integration test. _Acceptance: request routes gateway → Eureka → Job._
- **Inc 1 — Auth + JWT:** Auth service, recruiter/candidate roles, gateway-enforced
  security. _Acceptance: protected routes reject unauthenticated calls; roles honored._
- **Inc 2 — Job Service (full):** job posting/listing/status APIs + tests.
- **Inc 3 — Application Service:** apply/submit/status APIs, `resume_link`, **inter-service
  call to Job** (validate job), **load-balanced** (≥2 instances). _Acceptance: apply flow
  works end-to-end; LB verified._
- **Inc 4 — Recruitment Service:** shortlisting, `interview_status`, `final_status`,
  status tracking; Application → Recruitment link. _Acceptance: full hire workflow._
- **Inc 5 — Test + deploy hardening:** cross-service unit+integration coverage, contract
  tests, **Docker Compose deployment**, OpenAPI docs. _Acceptance: `up` runs the whole system; tests green._
- **Inc 6+ — Enhancements (only if approved):** UI, observability, AI + eval, notifications, etc.

_Each increment carries: objective · dependencies · components · tasks · tests · security
checks · acceptance criteria · verification. Expanded per-increment once scope (OQ5, OQ8) is set._
