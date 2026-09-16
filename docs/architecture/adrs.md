# Architecture Decision Records — PS044-SmartHire

_Consolidated ADR log (Nygard format). One numbered decision per section. Kept in a single
file for now; split into `adr/NNNN-*.md` later if the log grows._

**Class legend:** 🟦 SOURCE · 🟩 APPROVED ENHANCEMENT · 🟨 ASSUMPTION · 🟥 OPEN QUESTION · ✅ DECISION.

---

### ADR-0001 — Microservices architecture
**Status:** Accepted · **Class:** 🟦→✅
**Context:** Course is *SOA Programming and Microservices*; Project 44 mandates Job,
Application, Recruitment services + API Gateway, Auth, Eureka.
**Decision:** Build as independently deployable Spring Boot microservices behind an API
Gateway with Eureka discovery. Supersedes the Phase-0 modular-monolith idea.
**Consequences:** Satisfies the graded requirement; more moving parts than a monolith —
mitigated by a small, disciplined service set (ADR-0007, no gratuitous services).

### ADR-0002 — Java 21 LTS + Spring Boot 3.x + Spring Cloud
**Status:** Accepted · **Class:** 🟨→✅
**Context:** Eureka + API Gateway are Spring Cloud components; toolchain is Java/Spring.
**Decision:** Java 21 (LTS), Spring Boot 3.3+ (pin latest stable 3.x at bootstrap), Spring
Cloud release train aligned to that Boot version.
**Consequences:** Modern, supported, virtual-threads capable. Exact minor versions 🟨 pinned at bootstrap.

### ADR-0003 — Maven multi-module monorepo
**Status:** Accepted · **Class:** ✅
**Context:** `.gitignore` shows `target/` (Maven). Single team, academic delivery.
**Decision:** One Git repo, a Maven parent aggregator with one module per service + shared
`common` module (DTOs, error model, security utils). Each service builds its own Docker image.
**Consequences:** Simple dependency/version management and atomic changes; still independently
deployable. Trade-off: not independent repo lifecycles (acceptable for one team).

### ADR-0004 — Spring Cloud Gateway as the API Gateway
**Status:** Accepted · **Class:** 🟦→✅
**Decision:** Reactive Spring Cloud Gateway is the single entry point: routing (`lb://`),
authN (JWT validation), CORS, security headers, rate limiting (ADR-0011), request correlation.
**Consequences:** Meets the API-Gateway requirement; centralizes cross-cutting edge concerns.

### ADR-0005 — Netflix Eureka for service discovery
**Status:** Accepted · **Class:** 🟦→✅
**Decision:** A `service-registry` (Eureka Server); all services register as Eureka clients.
**Consequences:** Meets the requirement. (In a future K8s deployment, native discovery could
replace it — but source mandates Eureka, so Eureka stays. 🟥 revisit only if deployment demands.)

### ADR-0006 — Spring Cloud LoadBalancer (client-side)
**Status:** Accepted · **Class:** 🟦→✅
**Decision:** Client-side load balancing via Spring Cloud LoadBalancer (Ribbon is EOL); the
gateway and Feign clients resolve `lb://SERVICE-ID` across Eureka-registered instances.
**Consequences:** Meets the load-balancing requirement; verified by running ≥2 instances of a service.

### ADR-0007 — OpenFeign for synchronous inter-service communication
**Status:** Accepted · **Class:** 🟦→✅ (resolves OQ4/OQ-S2)
**Decision:** Declarative OpenFeign clients (LB-integrated) for the mandated
Application→Job→Recruitment calls; wrapped with Resilience4j (ADR + §25).
**Consequences:** Clear, testable inter-service contracts. Trade-off: synchronous coupling —
kept only for genuine request-time needs (e.g. validate job on apply); everything else async (ADR-0012).

### ADR-0008 — Schema-per-service in one PostgreSQL for R1 (path to DB-per-service)
**Status:** Accepted · **Class:** ✅ (resolves OQ-S1/item 7)
**Context:** Microservice ideal = database-per-service; academic/local ops favor simplicity.
**Decision:** Each service **owns its own schema** in a single PostgreSQL instance, with its
own DB user, **no cross-schema FKs or joins**, Flyway per service. Cross-service references
hold only remote IDs, validated via API.
**Consequences:** Preserves data ownership and a clean split path to separate databases,
while running one container locally. Trade-off: shared instance is a blast-radius/scaling
seam — documented; promote to database-per-service when a service needs independent scaling.

### ADR-0009 — Stateless JWT (RS256), auth-service as issuer
**Status:** Accepted · **Class:** 🟦→✅
**Decision:** `auth-service` issues short-lived access tokens (JWT, **RS256**) + refresh
tokens. Resource services are stateless OAuth2 resource servers verifying via the public key
(JWKS). Claims include `sub`, `roles`, `orgId`.
**Consequences:** No shared secret sprawl; services verify offline. Refresh handling + revocation via Redis (ADR-0011).

### ADR-0010 — RBAC + object-level ownership
**Status:** Accepted · **Class:** 🟦(roles)/🟩(org)→✅ (resolves OQ2)
**Decision:** Roles CANDIDATE, RECRUITER, ADMIN via Spring Security method security
(`@PreAuthorize`). Object-level checks enforce that a recruiter may act only on jobs/
applications of their `orgId`; a candidate only on their own applications/résumé.
**Consequences:** Meets JWT-auth requirement + approved org model. ADMIN is platform-level, not a recruitment actor.

### ADR-0011 — Redis for rate limiting + token revocation (opportunistic cache)
**Status:** Accepted · **Class:** 🟩→✅
**Decision:** R1 uses Redis for gateway rate limiting and a refresh-token/JWT denylist
(logout/revocation). Caching of hot reads (job listings/reference data) is opportunistic.
**Consequences:** Justified security/ops value now; avoids premature caching complexity.

### ADR-0012 — Async domain events via Redis Streams + transactional outbox
**Status:** Accepted · **Class:** 🟩→✅
**Context:** Notifications and audit must not couple to or block the core workflow.
**Decision:** State changes emit domain events using the **transactional outbox** pattern
(event row committed in the same local tx), relayed to **Redis Streams**; `notification-service`
and the audit consumer read via consumer groups. This complements — does not replace — the
SOURCE synchronous inter-service call.
**Consequences:** Decoupled, at-least-once delivery without adding Kafka/RabbitMQ.
**Upgrade path:** swap Redis Streams for a broker (Kafka/RabbitMQ) if durability/throughput
demands (R3). Minimal-viable fallback if time-constrained: direct async publish (documented).

### ADR-0013 — S3-compatible object storage for résumés (MinIO local)
**Status:** Accepted · **Class:** 🟩→✅ (resolves OQ3 storage)
**Decision:** `document-service` stores résumés in S3-compatible object storage (**MinIO**
locally); `resume_link` holds the object key/URL. Upload validated (MIME, size, AV hook);
access via short-lived pre-signed URLs.
**Consequences:** Provider-agnostic (same S3 API on AWS/GCP/Azure/MinIO); files never in the DB.
**Scope note (owner directive, 2026-09-15):** MinIO/S3 is an **implementation choice for the
approved résumé-upload enhancement, NOT a Project 44 source requirement.** The source only requires
`resume_link` as a URL reference. If a simpler compliant implementation is preferable, it is
allowed **behind the same `document-service` storage interface**: (a) a local mounted volume /
filesystem store for R1/local, or (b) the source-minimal path — `resume_link` as an external URL
with no upload at all. The storage backend is swappable; keep the port stable. Choose the simplest
option that satisfies the enhancement at each increment (decided at Inc 5).

### ADR-0014 — Vite + React + TypeScript SPA (not Next.js)
**Status:** Accepted · **Class:** 🟩→✅ (resolves OQ10)
**Context:** Separate Spring Boot API; no stated SSR/SEO need; provider-agnostic goal.
**Decision:** Vite + React + TS SPA calling the gateway. React Query (server state), React
Router, Zod validation. Access token in memory; refresh token in httpOnly Secure cookie.
**Consequences:** Deployable as static assets behind any CDN/nginx; avoids Next.js server
runtime lock-in. Trade-off: no SSR (not needed); add later if SEO/marketing pages appear.

### ADR-0015 — AI isolated in `ai-service`, advisory-only, R2
**Status:** Accepted · **Class:** 🟩→✅ (honors OQ-AI1/AI2)
**Decision:** AI lives only in `ai-service` (R2), integrating Claude API. Advisory outputs
(match scores, summaries, JD/recruiter assistance) with rationale + model/prompt-version
metadata. PII minimized/redacted before prompts; résumé text treated as untrusted input
(prompt-injection defenses); outputs schema-validated; gated by an evaluation harness;
protected characteristics never used as inputs. Never in the mandatory workflow path.
**Consequences:** Core recruitment works without AI; safety/fairness boundaries enforced by
design. Legal fairness metric deferred to Phase 3/4 compliance decision (🟥).

### ADR-0016 — RFC 9457 Problem Details for errors
**Status:** Accepted · **Class:** ✅
**Decision:** All services return `application/problem+json` via Spring `ProblemDetail` +
`@RestControllerAdvice`; consistent schema with `type/title/status/detail/instance` +
correlation id; no stack traces or internals leaked.
**Consequences:** Uniform, client-friendly, secure errors.

### ADR-0017 — Container-first, provider-agnostic deployment; Compose for local full-system
**Status:** Accepted · **Class:** 🟩/OQ7→✅
**Decision:** Every service ships a multi-stage Docker image (slim JRE). `docker-compose.yml`
runs the whole system locally (gateway, eureka, services, postgres, redis, minio, mailhog,
frontend). No cloud SDK lock-in; twelve-factor config. Cloud/K8s manifests deferred.
**Consequences:** `docker compose up` = full local system (acceptance AC-8); cloud port later is straightforward.

### ADR-0018 — Local transactions + eventual consistency; no distributed transactions in R1
**Status:** Accepted · **Class:** ✅
**Decision:** Each service is its own ACID boundary; cross-service consistency is eventual via
the outbox/events (ADR-0012). No 2PC. No saga in R1 (no cross-service write transaction
exists — apply-flow validates Job read-only then writes Application locally). Introduce a
saga only if/when a true multi-service write transaction appears.
**Consequences:** Simple and correct for current flows; avoids premature orchestration.

### ADR-0019 — GitHub Actions CI with security automation
**Status:** Proposed · **Class:** 🟨🟥
**Context:** Git is used; GitHub assumed but not confirmed.
**Decision (pending OQ-CI):** CI on GitHub Actions: build → unit+integration (Testcontainers)
→ dependency + SAST + secret scanning → build images. Confirm the CI platform.
**Consequences:** Automated quality/security gates. 🟥 confirm platform before wiring.
