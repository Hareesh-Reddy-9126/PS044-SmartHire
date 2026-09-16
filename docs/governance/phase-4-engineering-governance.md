# Phase 4 — Engineering Governance — PS044-SmartHire

_Canonical governance, engineering standards, verification policy, and AI-development controls.
Derived from `CLAUDE.md`, the approved Phase-3 architecture (`../architecture/phase-3-technical-architecture.md`
+ `../architecture/adrs.md`), and the requirements register (`../discovery/requirements-register.md`).
**No application code is written in Phase 4.** These rules bind Phase 5+ implementation._

## Classification legend (applied to every rule)

- 🟦 **SOURCE REQUIREMENT** — explicit in the Project 44 PDF.
- 🟩 **APPROVED ENHANCEMENT** — owner-approved 2026 scope; not in the PDF.
- ✅ **DECISION** — a concrete standard chosen here (does not come from the source).
- 🟨 **ASSUMPTION** — inference, explicitly labeled.
- 🟥 **OPEN QUESTION** — needs an owner decision; left undecided.

> **Anti-overhead guardrail (owner directive + ponytail):** no framework, tool, service, or
> process is added merely to look enterprise-grade. Every gate below either protects a mandatory
> requirement, prevents a real regression, or enforces a security/PII boundary. Right-sized for
> **one team, academic delivery, production quality**. Ceilings are marked; nothing is theater.

---

## A. Principles, structure & conventions

### 1. Engineering principles — ✅ (grounded in `CLAUDE.md`)
Simple/maintainable over clever; **YAGNI** (build at the first sufficient rung); security-first at
every trust boundary; **test before done**; incremental changes over rewrites; docs stay synced
with code; no unjustified dependencies; explain trade-offs before architectural change. Root-cause
fixes, not symptom patches. These are firm project rules, not preferences.

### 2. Repository structure conventions — ✅ (ADR-0003)
One Git monorepo, Maven parent aggregator:
```
/pom.xml                 (parent/aggregator, dependency & plugin management)
/common/                 (shared library: DTOs, Problem-Details, JWT/security utils, event schemas)
/services/
  api-gateway/  service-registry/  auth-service/  job-service/
  application-service/  recruitment-service/  document-service/  notification-service/
  (ai-service/ config-server/ analytics-service/  — added in R2/R3)
/frontend/               (Vite + React + TS SPA)
/deploy/                 (docker-compose.yml, .env.example, healthcheck config)
/docs/                   (discovery, architecture, governance, traceability)
```
🟨 `ai-service`, `config-server`, `analytics-service` directories are created only when their
release begins — not scaffolded empty in advance.

### 3. Service / package / module conventions — ✅
- Maven module id: `smarthire-<service>` (e.g. `smarthire-job-service`).
- Java base package: `com.smarthire.<service>`; layered sub-packages: `api` (controllers/DTOs),
  `domain` (entities, state machines, rules), `service` (application logic/transactions),
  `infra` (persistence, Feign clients, storage), `config`.
- One bounded context per service; **no shared JPA entities across services** — cross-service
  data crosses only as DTOs (in `common`) or remote IDs.
- 🟥 **OQ-PKG:** final `com.smarthire` group id / artifact coordinates confirmed at bootstrap.

### 4. Java / Spring Boot coding standards — ✅
- **Java 21**; **constructor injection only** (no field `@Autowired`); DTOs are `record`s;
  entities are classes with explicit state-transition methods (no anemic setters for status).
- **Spotless + Google Java Format** enforced in CI (fail on unformatted). **Checkstyle**
  minimal ruleset (naming, imports, no wildcard imports).
- No `System.out`; SLF4J only. No swallowed exceptions. Prefer immutability and package-private
  visibility. **No Lombok in R1** (records + IDE cover it — ponytail; add only if boilerplate
  measurably hurts).
- 🟨 Exact Spotless/Checkstyle rule files pinned at bootstrap.

### 5. React / TypeScript coding standards — ✅ (ADR-0014)
- **TypeScript strict mode**; **no `any`** (use `unknown` + narrowing). **ESLint + Prettier**
  in CI. Functional components + hooks only.
- **React Query** for all server state (no ad-hoc fetch-in-effect); **React Router**; **Zod**
  for all external input parsing (mirrors server validation, §10).
- No secrets/tokens in `localStorage` (access token in memory, refresh in httpOnly cookie).
- Accessibility basics are non-negotiable (labels, focus, semantic HTML) — never simplified away.

## B. API & data contracts

### 6. REST / API standards — 🟦(APIs required)/✅
Resource-oriented REST/JSON; plural nouns; standard verbs; correct status codes; pagination
(`page`,`size`) + filtering on list endpoints; idempotent GET/PUT/DELETE; **RFC 9457** errors
(§9). Public vs protected routes explicitly declared (deny-by-default at gateway).

### 7. API versioning — ✅ (Phase-3 §15)
URI prefix **`/api/v1/...`** from day one. Breaking changes → new major version path; additive
changes stay in-version. No unversioned public endpoints.

### 8. OpenAPI documentation rules — 🟩(E5)/✅
**springdoc-openapi** per service; every endpoint documented with request/response schemas + at
least one example and its error responses. The generated spec is a CI artifact. An endpoint
without an OpenAPI operation fails review.

### 9. Error-response contract — ✅ (ADR-0016)
All services return `application/problem+json` (RFC 9457) via a shared base in `common`:
`type/title/status/detail/instance` + `correlationId`. **No stack traces, SQL, class names, or
internal messages** to clients. Validation errors carry field-level details. Illegal state
transitions → 409/422 problem.

### 10. Validation standards — ✅ (Phase-3 §17)
**Bean Validation (`@Valid`)** on every inbound DTO at every service boundary — the gateway is
not trusted for payload validation. Server is authoritative; the SPA mirrors with Zod for UX only.
Domain invariants (state-machine legality, ownership) validated in the domain/service layer.

### 11. Exception-handling standards — ✅
`@RestControllerAdvice` per service maps typed domain exceptions → Problem Details. Never catch-and-
ignore; never `catch (Exception)` without rethrow/log. Expected domain failures are typed
exceptions, not generic `RuntimeException`. Unexpected errors → 500 problem with correlationId,
full detail logged server-side only.

### 12. Database / migration standards — ✅ (ADR-0008)
**Flyway per service**; migrations `V{n}__snake_case.sql`, **forward-only**, immutable once merged
(new migration to change, never edit a shipped one). **Schema-per-service, own DB user, no
cross-schema FKs/joins.** Destructive migrations require explicit review + a rollback note. No
Hibernate `ddl-auto` beyond `validate` in any non-local profile.

### 13. Transaction boundaries — ✅ (ADR-0018)
Local `@Transactional` at the service layer; one service = one ACID boundary. **No distributed
transactions / 2PC / saga in R1.** The outbox event row commits in the **same** local transaction
as the state change. Cross-service consistency is eventual (§ inter-service).

### 14. Inter-service communication rules — ✅ (ADR-0007)
**OpenFeign only** for synchronous calls, always via `lb://SERVICE-ID`, always wrapped with
Resilience4j (timeout mandatory). **No service reads another service's database.** Cross-service
references are remote IDs validated by API. Async side effects go through outbox → Redis Streams.
The mandated chain is Application→Job (validate) and Application→Recruitment (advance).

## C. Security, secrets & observability

### 15. Security engineering standards — 🟦(security-first)/✅
Trust-boundary validation; least privilege (per-schema DB users, minimal token scopes);
defense-in-depth (edge + per-service authz); TLS in transit; **OWASP-informed baseline** (injection,
authn, authz, sensitive-data exposure, SSRF on the résumé/URL path). Security review is part of DoD
(§32). STRIDE model in Phase-3 §31 is the reference threat catalogue.

### 16. JWT / RBAC / authorization rules — 🟦/✅ (ADR-0009/0010)
RS256 JWT, `auth-service` sole issuer, offline verify via JWKS; short-lived access + rotating
refresh + Redis revocation. **`@PreAuthorize` for role checks AND explicit object-level ownership
checks** (`sub`/`orgId`) on every resource mutation/read of owned data. Deny-by-default. ADMIN =
platform ops, never a recruitment decision-maker. **No HIRING_MANAGER role in MVP** (D7).

### 17. Secret / configuration rules — 🟦(never expose secrets)/✅
**No secrets in code, config, or git history.** `.env*` git-ignored (verified); `.env.example`
lists **keys only, never values**. Twelve-factor env config. RS256 **private key injected per
environment, never committed**. Secrets never logged, never sent to the AI provider. Pre-commit
secret scan (§29). 🟥 **OQ-KEY:** key custody + rotation cadence (Phase 4/deployment).

### 18. Logging standards — ✅
**Structured JSON** logs with propagated `correlationId`; SLF4J; sane levels (no DEBUG in prod
profile). **Never log** PII (résumé content, emails beyond necessity), secrets, tokens, or full
request bodies. Log security events (auth failures, authz denials) at WARN.

### 19. Metrics / tracing / observability standards — 🟩(E3)/✅
R1: **Sentry** (services + SPA) for errors; **Micrometer + Actuator** metrics; `/health` per
service (used by Compose healthchecks). CorrelationId flows end-to-end from the gateway.
**Distributed tracing (OpenTelemetry) deferred to R2/R3** — the correlation id already threads
requests, so tracing is additive, not a rework.

### 20. Audit logging standards — 🟩(BR-6)/✅
Sensitive actions (auth events, status transitions, user/role management, data access to
applications, and any R2 AI-assisted recommendation) emit an audit event (outbox → stream) to an
**append-only** store: `actor, action, entity, before/after (non-sensitive), timestamp,
correlationId`. Audit is cross-cutting, **not** a separate microservice in R1. 🟥 retention policy
open (governance/deployment).

## D. Testing & verification

### 21. Testing pyramid & coverage policy — 🟦(tests required)/✅
Pyramid: many **unit** → fewer **integration** → focused **contract** → thin **E2E**.
**Coverage DECISION:** ≥ **80% line/branch on `domain` + `service` packages** (business logic,
state machines, authz); DTOs, config, generated code, and framework glue are excluded. Coverage is
a **guardrail, not a vanity gate** — a meaningful test for each rule beats a number. Owner may
adjust the threshold.

### 22. Unit-testing rules — ✅
JUnit 5 + Mockito; no Spring context; millisecond-fast. **Mandatory coverage of:** every state
transition (legal + illegal, §26 machines), every authorization rule (owner vs non-owner vs
cross-org), and every validation constraint. Illegal transitions must assert rejection.

### 23. Integration-testing rules — 🟦/✅
**Testcontainers** with **real PostgreSQL + Redis** (no H2 substitution — parity matters). Per
service: repository/migration tests (Flyway runs), security-filter tests (JWT accepted/rejected),
and the persistence side of each API. Gateway routing verified against a live registered service.

### 24. Contract-testing rules — ✅
**Spring Cloud Contract** on every Feign boundary (Application↔Job, Application↔Recruitment). The
provider publishes contracts; consumers verify against stubs. A provider change that breaks a
consumer **fails CI** — this protects the source-mandated inter-service chain (C5/R10).

### 25. End-to-end testing rules — 🟩/✅
**Playwright** against the Compose stack for **core journeys only** (register/login, post job,
apply, shortlist→status). Thin in R1 (ponytail — E2E is expensive; unit+integration carry the
load). No exhaustive UI matrix.

### 26. Testcontainers strategy — ✅
Shared container per test class (or a singleton container base) with reuse enabled to keep CI fast;
Ryuk handles teardown. Same Postgres/Redis image tags as production Compose (env parity). No test
depends on external network services.

### 27. CI/CD quality gates — 🟩(E4)/🟥(platform)
Pipeline (per PR): **build → unit → integration (Testcontainers) → contract → format/lint →
security scan (§29) → build images**. Merge is blocked unless all pass. CD (publish/deploy) is
deferred with the cloud target. 🟥 **OQ-CI:** confirm platform (GitHub Actions assumed since
origin is GitHub; ADR-0019 pending) before wiring.

## E. Process, dependencies & release

### 28. Dependency management & upgrade policy — 🟦(no unjustified deps)/✅
**Spring Boot + Spring Cloud BOM** govern versions; no floating/`+` versions. A **new dependency
requires a one-line justification** in the PR (which rung of the ladder failed). **Dependabot**
(🟨 assumes GitHub) opens grouped update PRs; security patches prioritized; majors reviewed. Remove
unused deps on sight.

### 29. Static analysis / security scanning — ✅
Lean, high-signal set: **Spotless/Checkstyle** (style), **SpotBugs** (bugs), **OWASP
Dependency-Check** (vulnerable deps), **gitleaks** (secret scan, also pre-commit), ESLint (SPA).
SAST beyond SpotBugs (e.g. CodeQL) is **R3** unless the platform provides it free (🟥 with OQ-CI).
A high-severity finding blocks merge.

### 30. Git / branch / commit / PR conventions — ✅
Trunk-based on `main`; **short-lived feature branches** (`feat/…`, `fix/…`, `chore/…`); **no direct
commits to `main`**. **Conventional Commits** (`type(scope): summary`). **Squash-merge** via PR.
Every PR references the increment/requirement it advances and updates the traceability matrix if
scope changes. Commit attribution per repo policy.

### 31. Code-review policy — ✅/🟨
Every change is reviewed before merge. **Team reality:** effectively solo + AI, so the review gate
is: (a) the **code-review** skill/plugin runs an adversarial review, (b) the author works a
**self-review checklist** (DoD §32), (c) the human owner approves the PR. AI review does **not**
replace the human approval gate (§42). 🟥 add a second human reviewer if the team grows.

### 32. Definition of Done — ✅ (extends `CLAUDE.md` workflow)
A change is **Done** only when: compiles + all tests green (unit/integration/contract as
applicable); new logic has a test (including a **failing-first** test for bug fixes, §45); security
& authz reviewed; input validated; no secrets/PII leaked; error contract honored; OpenAPI + docs
updated; acceptance criteria for the increment met; **traceability matrix updated**; ArchUnit/fitness
checks pass (§46). "Works on my machine" ≠ Done — `docker compose up` health-verified for
integration-affecting changes.

### 33. Release / versioning strategy — ✅
Artifacts use **SemVer** (`MAJOR.MINOR.PATCH`); Docker images tagged with the same version + git
SHA. Releases align to the R1/R2/R3 increments (§50); each release tagged in git with a short
changelog. `latest` is never relied on in Compose (pinned tags).

### 34. Docker / container standards — ✅ (ADR-0017)
**Multi-stage** builds; slim JRE base (pinned digest); **non-root** user; `HEALTHCHECK`; no secrets
baked into layers; `.dockerignore` excludes source/tests/`.env`; one process per container;
build-time deps not shipped in the runtime image. Same image promotes across environments.

### 35. Environment management — ✅ (Phase-3 §28)
Profiles `local` / `ci` / `prod`; **config differs only by env vars**; build-once/deploy-many.
Local secrets in a git-ignored `.env`; CI uses ephemeral Testcontainers; prod config injected by
the platform. No environment-specific code branches.

## F. Non-functional & resilience

### 36. Performance testing policy — ✅ (Phase-3 §32)
R1 optimizes **correctness and demonstrability**, not throughput. The only R1 performance-adjacent
gate is the **load-balancing verification** (≥2 job-service instances, AC-4). Formal load/SLO
testing is **R3**. 🟥 No performance rubric exists in the source (OQ9) — none invented. No premature
optimization.

### 37. Resilience / failure-testing policy — ✅ (Phase-3 §25)
Resilience4j policies are **tested, not just configured**: an integration test injects a
job-service outage and asserts **apply fails closed** with a clear Problem Detail (never accepts an
application against an unvalidated job). Async consumers tested for retry + dead-letter. Timeouts
mandatory on every Feign call.

## G. Documentation & decisions

### 38. Documentation standards — 🟦(keep docs synced)/✅
Docs live under `/docs` and **stay synced with code** (a change that alters behavior updates its
doc in the same PR). Each service ships a short `README` (purpose, endpoints, run/test). OpenAPI is
the API's living contract. The discovery/architecture/governance/traceability set is canonical;
prose stays lean (no duplicated rationale — reference ADRs).

### 39. ADR governance — ✅
Any architecturally significant decision → a new numbered **ADR** (Nygard format) in `adrs.md`
(split to `adr/NNNN-*.md` if the log grows). ADRs are **superseded, never edited away** (keep the
history). Changing an approved Phase-3 decision requires a superseding ADR **and** owner approval
(§42). Class tag on every ADR.

## H. AI-development governance

### 40. AI-assisted development policy — ✅ (grounds the whole discovery process)
Claude Code builds this project under human governance: **AI proposes, human approves** at every
phase gate. AI must **never invent requirements or product scope**, must preserve the
SOURCE/ENHANCEMENT/ASSUMPTION/OPEN-QUESTION/DECISION classification, must use project skills when
relevant, and must never expose secrets. Discovery→governance phases produce **no application
code**; code begins only after Phase-4 approval and per-increment gates (§50).

### 41. Claude Code agent / subagent responsibilities — ✅/🟨
The main agent orchestrates and holds context; **subagents** handle bounded research/exploration
(read-only `Explore`), planning, and adversarial code review. **Subagent output is verified by the
main agent before use** (never merged blind). Subagents get least-privilege tools for their task.
No agent self-approves a phase gate or merges to `main`.

### 42. Human approval gates — 🟦(owner directive)/✅
Hard STOP + owner approval required at: **each phase boundary**; **any change to an approved
architecture decision** (needs a superseding ADR); **any scope change** (source vs enhancement);
**before writing application code**; **before external/outbound actions** (publishing, deploying).
An approval in one context does not extend to the next.

### 43. AI security & prompt/data-handling rules — 🟩(D9)/✅
Applies to the **product's** AI feature (R2, `ai-service`): **protected characteristics never used
as inputs**; **PII minimized/redacted before prompts** where practical; résumé/user text is
**untrusted** (prompt-injection defenses; treat model output as data, never as commands/SQL);
**no secrets in prompts**; **outputs schema-validated**; log **model/prompt/version metadata only**,
never sensitive content in the clear. AI output is **advisory** — never an autonomous decision
(BR-4). 🟥 legal fairness metric open (OQ-AI1).

### 44. AI-generated-code verification — ✅
AI-generated code is held to the **same DoD as human code** (§32): reviewed, tested, security-
checked, traceable — **no exceptions and no "the AI wrote it so it's fine."** Generated code that
touches auth, authz, money/state transitions, or PII gets extra scrutiny and a dedicated test.
Unverified AI code is never merged.

## I. Regression, fitness & readiness

### 45. Regression prevention — ✅
**Every bug fix starts with a failing test** that reproduces it, then the root-cause fix (ponytail:
fix once where all callers route through), then green. Contract tests guard the inter-service chain;
CI re-runs the full suite on every PR. No fix merges without its regression test.

### 46. Architectural fitness / drift detection — ✅
**ArchUnit** tests (lightweight, justified) enforce, in CI: layered package dependencies (`api →
service → domain`, no reverse), **no cross-service entity/DB imports**, no `System.out`, controllers
return DTOs not entities, and Feign clients live only in `infra`. The **traceability matrix** is the
human-level drift check — a mandatory requirement that loses its component/test mapping is drift and
blocks release. Drift is caught by CI, not by memory.

### 47. Production readiness checklist — ✅ (per service, before "release")
`/health` green · config fully externalized · secrets injected (none in image/repo) · structured
logs + Sentry wired · metrics exposed · RFC 9457 errors · authz enforced (role + ownership) ·
migrations run clean · integration + contract tests green · resilience policies tested ·
`docker compose up` brings the service up healthy · OpenAPI published. 🟨 Cloud-specific items
(TLS termination, secrets store) added with the deployment target (OQ7).

### 48. Incident / debugging expectations — ✅
Use the **systematic-debugging** skill: reproduce → **failing test** → find **root cause** (grep all
callers, fix at the shared choke point) → verify → add regression test (§45). Use the `correlationId`
to trace across services. **No symptom patches**, no "sprinkle a null check and move on." Report what
was wrong, the fix, and what was verified.

### 49. Academic Project 44 compliance gate — 🟦/✅ (hard gate)
**Non-negotiable release gate for R1:** every 🟦 mandatory requirement (microservices, API Gateway,
Eureka, load balancing, inter-service comm Application→Job→Recruitment, JWT for both roles, the
Job/Application/Recruitment relational model, unit+integration tests, deployment) is **demonstrably
implemented and tested**, proven via the traceability matrix (all rows mapped **and** green). **R1
must be independently demonstrable without any AI feature** (D8). Enhancements may not compromise or
obscure a mandatory requirement. If the matrix isn't fully green, R1 is **not done**.

### 50. Phase-by-phase implementation governance — ✅ (Phase-3 §33 increments)
Implementation follows the vertical-slice increments **Inc 0 → Inc 6** (skeleton → auth → job →
application+LB → recruitment → R1 enhancements → test/deploy hardening). **Each increment carries its
own objective, tests, DoD, and acceptance criteria, and ends at a STOP gate for owner approval.** No
increment starts before the previous is Done; no skipping ahead to enhancements before the mandatory
slice is green (§49). AI features remain gated to R2.

---

## Consistency check vs approved Phase 3 (required verification)

Governance was cross-checked against every locked Phase-3 decision — **no contradictions found**:

| Phase-3 decision | Governance rule(s) | Consistent? |
|---|---|---|
| Microservices, small service set (ADR-0001) | §2, §3, §46 (no cross-service DB) | ✅ |
| OpenFeign sync + Resilience4j (ADR-0007, D11) | §14, §37 | ✅ |
| Schema-per-service, Flyway (ADR-0008, D12) | §12, §46 | ✅ |
| RS256 JWT + JWKS (ADR-0009) | §16, §17 | ✅ |
| RBAC + object ownership, ADMIN ops, no HIRING_MANAGER (ADR-0010, D6/D7) | §16 | ✅ |
| Local tx + eventual consistency, no distributed tx (ADR-0018, D13-tx) | §13 | ✅ |
| State machines (D5) | §22 (illegal-transition tests), §9 (409/422) | ✅ |
| AI advisory, R2, off critical path (ADR-0015, D8) | §40, §43, §49 (R1 without AI) | ✅ |
| AI fairness/PII posture (D9) | §43 | ✅ |
| RFC 9457 errors (ADR-0016) | §9, §11 | ✅ |
| Container-first, provider-agnostic, Compose (ADR-0017, D2) | §34, §35, §47 | ✅ |
| Vite React SPA, token handling (ADR-0014) | §5 | ✅ |
| Tests: unit+integration+contract (source C8) | §21–26 | ✅ |
| CI platform pending (ADR-0019) | §27, §29 (🟥 OQ-CI preserved) | ✅ |

**Open questions carried forward (unchanged, not introduced):** OQ-CI (platform), OQ-KEY (key
rotation), OQ-AI1 (legal fairness metric), OQ7 (cloud target), OQ9 (no rubric — source is baseline),
OQ-PKG (group id). None contradicts Phase 3; all are Phase-4/deployment-time items.
