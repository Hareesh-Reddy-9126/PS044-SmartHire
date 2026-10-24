# SmartHire — PROJECT_CONTEXT.md

Persistent project context for future Claude/Kilo Code sessions. Concise, durable, and kept in sync with implementation. Verified facts are marked [V]; facts inherited from prior agent reports are marked [R] (reported, pending independent re-verification).

## 1. Project purpose and technology stack

- **Purpose**: Online job recruitment platform (microservices + React SPA) for course project 24SDCS03A/R — SOA Programming and Microservices (AY 2026-27). [V: README.md]
- **Status**: Phase 4 (Quality/Security/CI) complete. Review-1 complete (Inc 0+1+2+3); Inc 4/5 services present; Phase 2 backend integration complete (ai-service tests pass). [V: README.md, phase2_report.md, PROJECT_STATUS.md]
- **Backend**: Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0 (Moorgate), Spring Cloud Gateway, Eureka service discovery, OpenFeign inter-service clients, Resilience4j (circuit breaker/retry/bulkhead), Spring Security 6 (OAuth2 resource server + JWKS), Spring Data JPA, Flyway migrations, PostgreSQL 16, Redis (refresh-token store + JWT jti denylist), springdoc-openapi (Inc 5). [V: pom.xml, docker-compose.yml]
- **Frontend**: React 18, TypeScript 5, Vite 5, Tailwind CSS, React Router 6, Vitest + Testing Library. [V: frontend/package.json, frontend/README.md]
- **Build tool**: Maven multi-module (root `pom.xml` aggregator), wrapper conventions. Host JDK 24 locally; build targets Java 21 bytecode. [V: pom.xml, environment memory]
- **CI**: GitHub Actions (`.github/workflows/ci.yml`) — JDK 21, `mvn -B verify` excluding `services/ai-service`, plus Spotless, SpotBugs, OWASP Dependency-Check jobs. [V: ci.yml]

## 2. Frontend

- **Directory**: `frontend/`
- **Framework**: React 18 + TypeScript + Vite 5
- **Dev server**: `npm run dev` (Vite on `http://localhost:5173`), proxies `/api`, `/oauth2`, `/actuator`, `/v3` to gateway `http://localhost:8080`
- **Build**: `npm run build` (`tsc && vite build` to `dist/`)
- **Typecheck**: `npm run typecheck` (`tsc --noEmit`)
- **Lint**: `npm run lint` (ESLint, zero warnings)
- **Test**: `npm run test:run` (Vitest, 49 tests across 8 files, per frontend README [V])
- **API config**: All calls via API Gateway `/api/v1/*`; `VITE_API_BASE_URL` env var for non-proxy deployments; `VITE_DEBUG` for logging
- **Auth**: Access tokens in memory only (never localStorage); refresh token HttpOnly cookie; proactive refresh via `AuthContext`; role-gated client routes (ProtectedRoute/WorkspaceRedirect); real authorization enforced by gateway + services
- **Roles/routes**: Public `/jobs`, `/jobs/:jobId`, `/login`, `/register`; CANDIDATE `/candidate/applications` (+apply/progress/documents/notifications/profile); RECRUITER `/recruiter/jobs` (+create/detail/edit/recruitments/application lookup/profile); ADMIN `/admin/overview` (+recruitments/application lookup/profile)
- **Known gaps [R: memory]**: Candidate-facing `ProgressDetailPage.tsx` and `ApplicationDetailPage.tsx` omit interview fields despite API returning them; recruiter application list unsupported by backend (lookup by ID only); no password reset/profile-edit endpoints; AI features out of scope for frontend

## 3. Backend services

| Module | Directory | Port | Responsibility |
|---|---|---|---|
| service-registry | `services/service-registry` | 8761 | Eureka server (service discovery) |
| api-gateway | `services/api-gateway` | 8080 | Spring Cloud Gateway, single public entry point, edge auth/rate limiting |
| auth-service | `services/auth-service` | 8082 | JWT issuance + JWKS, refresh-token store (Redis), seed accounts |
| job-service | `services/job-service` | 8081 | Job aggregate, `job_svc` schema |
| application-service | `services/application-service` | 8083 | Applications, `application_svc` schema, Feign calls to job/notification |
| recruitment-service | `services/recruitment-service` | 8084 | Recruitment workflow, `recruitment_svc` schema, Feign calls to application/job |
| document-service | `services/document-service` | 8085 | Document management, local storage abstraction, `document_svc` schema |
| notification-service | `services/notification-service` | 8086 | Application status notifications, logging sender (R1), `notification_svc` schema |
| ai-service | `services/ai-service` | — | AI matching/evaluation (security config fixed, compiles, all 52 unit tests pass) [V] |
| common | `common` | — | Shared correlation-id filter + RFC 9457 Problem Details advice |

Each service has its own `pom.xml`, `src/main/java`, `src/main/resources/application.yml`, and `src/test`. Flyway migrations live under `src/main/resources/db/migration/` (e.g. `V2__interview_schedule_details.sql` in recruitment-service). [V: directory listing, docker-compose.yml]

## 4. API gateway and service discovery

- Gateway (`services/api-gateway`) is the single public entry point on port 8080; routes resolve service names through Eureka (`lb://SERVICE-NAME`).
- Eureka server (`service-registry`) on port 8761; dashboard at `http://localhost:8761`.
- Services register with Eureka; gateway fetches auth-service JWKS directly over the internal network (`http://auth-service:8082/oauth2/jwks`, not `lb://`).
- JWT verification per service uses its own `*_JWK_SET_URI` pointing at auth-service JWKS.
- Inter-service Feign calls are load-balanced through Eureka; protected by Resilience4j `@CircuitBreaker`/`@Retry`/`@Bulkhead`.
- `application-service/NotificationClient` now has all three annotations (`@Retry` added in Phase 2). [R: phase2_report.md]

## 5. Databases, migrations, inter-service communication, configuration

- **Database**: Single PostgreSQL 16 instance (`postgres:16-alpine`), shared app user, per-service schemas (`auth_svc`, `job_svc`, `application_svc`, `recruitment_svc`, `document_svc`, `notification_svc`). Not published to host by default. [V: docker-compose.yml]
- **Migrations**: Flyway, `src/main/resources/db/migration/` per service (e.g. `V2__interview_schedule_details.sql`). Per-schema least-privilege users deferred to later increments. [V: docker-compose.yml, README]
- **Redis**: `redis:7-alpine`, refresh-token store + shared JWT jti denylist (`revoked:jti:*`), used by auth-service and gateway. [V: docker-compose.yml]
- **Inter-service communication**: OpenFeign clients + Eureka client-side load balancing; Resilience4j fault tolerance on all Feign clients (CB/Retry/Bulkhead). [V: phase2_report.md, backend_integration_gaps_summary.md]
- **Configuration**: `application.yml` per service; `application-prod.yml` and `application-local.yml` present for api-gateway/application-service/auth-service/job-service/recruitment-service; secrets from `deploy/.env` (git-ignored, never committed); `deploy/.env.example` lists keys only.
- **Required env vars**: `POSTGRES_USER`, `POSTGRES_PASSWORD` (fail fast via `${VAR:?...}`); optional `POSTGRES_DB`, `GATEWAY_CORS_ALLOWED_ORIGINS`, seed account vars, `AUTH_JWT_PRIVATE_KEY`.

## 6. Maven module structure and verified build/test commands

Root `pom.xml` modules: 7 in committed `HEAD` (`common`, `service-registry`, `api-gateway`, `job-service`, `auth-service`, `application-service`, `recruitment-service`); 10 in working tree (adds `document-service`, `notification-service`, `ai-service` — Claude's Phase 2 work, untracked). All 10 compile successfully. [V: `mvn compile`]

- **Compile all**: `mvn compile` (or `mvn -pl <module> compile`) — [V: all 10 modules SUCCESS on JDK 24]
- **Test one module**: `mvn -pl services/<module> test` — verified results: application-service 137/0 [V], auth-service 55/0 [V], job-service 78/0 [V], recruitment-service 122/0 [V], document+notification+common 44/0 [V]; ai-service 52/0 [V]
- **Full quality gate**: `mvn verify` (CI excludes `services/ai-service`: `mvn -B verify -pl '!services/ai-service' -am`) — cannot run locally (Testcontainers/PostgreSQL unavailable) [R: environment memory]
- **Spotless check**: `mvn spotless:check` — passes on all modules [V: `mvn spotless:check`]
- **SpotBugs**: `mvn spotbugs:check` — passes on all modules (0 bugs) [V: `mvn spotbugs:check`]
- **ArchUnit**: Warnings on JDK 24 class files [R: environment memory]
- **Testcontainers/real Postgres**: Required for integration tests; Docker/Testcontainers unavailable locally, so integration tests and live Swagger verification cannot run locally [R: environment memory]
- **AI service tests**: 52 tests, 0 failures, 0 errors [V: `mvn -pl services/ai-service test`]

## 7. Docker Compose entry points and CI workflows

- **Docker Compose**: `deploy/docker-compose.yml` (stack name `smarthire`); optional `deploy/docker-compose.pgadmin.yml` for pgAdmin on 5050. Run: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build`. Compose wait order: Postgres → service-registry → job-service → api-gateway (plus auth/application/recruitment/document/notification). [V: docker-compose.yml]
- **CI workflow**: `.github/workflows/ci.yml` — jobs: `test` (Java 21, `mvn -B verify -pl '!services/ai-service' -am`), `spotless`, `spotbugs`, `security-scan` (OWASP Dependency-Check, push/main PR only). [V: ci.yml]
- **Postman collection**: `deploy/postman/SmartHire-Review1-E2E-Collection.json` + environment (manual E2E only, no automated CI E2E) [R: environment memory/testing gaps]

## 8. Important architecture and coding conventions

- **Project rules** (`CLAUDE.md`): new project, no reviving old implementations; simple maintainable solutions; no unjustified dependencies; never expose secrets; validate with tests; incremental changes; synchronized docs; use project skills; security-first; inspect before creating/changing; explain architectural trade-offs. [V: CLAUDE.md]
- **Java**: Java 21 bytecode/language level regardless of host JVM; `-parameters` compiler flag for Jackson record deserialization (JEP 118); `-Dnet.bytebuddy.experimental=true` for Surefire/Failsafe on JDK 24 host. [V: pom.xml]
- **DTOs**: `RegisterRequest`/`LoginRequest` use immutable classes with `@JsonCreator`/`@JsonProperty` (not records) to avoid Jackson 2.18.2 deserialization issues on JDK 21+ [R: memory]
- **Formatting**: Spotless + Google Java Format (`google-java-format 1.22.0`), `removeUnusedImports`, `trimTrailingWhitespace`, `endWithNewline`; hard gate at `verify` phase. [V: pom.xml]
- **Static analysis**: SpotBugs `effort=Max`, `threshold=Medium`, `failOnError=true`; SpotBugs annotations `provided` scope for targeted `@SuppressFBWarnings`. [V: pom.xml]
- **ArchUnit**: Architecture rules enforced in tests. [V: pom.xml]
- **Secrets**: Never in source/committed config; runtime from environment; `deploy/.env` git-ignored. [V: README.md, docker-compose.yml]
- **API style**: REST, RFC 9457 Problem Details for errors, correlation-id header on every response, UUID primary keys for cross-service opacity. [V: README.md, common module description]

## 9. Links and paths to key documentation and tests

- `CLAUDE.md` — project rules and development workflow (root)
- `README.md` — full project overview, Review-1 demo setup, Postman E2E flow (root)
- `frontend/README.md` — frontend stack, scripts, architecture, auth, API surface, roles
- `phase2_report.md` — Phase 2 backend integration report (root, do not overwrite; Claude owns it)
- `backend_integration_gaps_summary.md` — AI service security config + backend integration gap summary (root)
- `.github/workflows/ci.yml` — CI pipeline definition
- `deploy/docker-compose.yml` — local demonstration stack
- `deploy/.env.example` — environment variable template (keys only)
- `deploy/postman/` — Postman E2E collection + environment
- Tests: `services/*/src/test/java/...`; frontend `frontend/src/` (Vitest, 49 tests/8 files per README)

## 10. Known technical constraints future agents must account for

- Docker and Testcontainers unavailable locally → integration tests, live Swagger runtime verification, and `mvn verify` full gate cannot run locally [R: environment memory]
- Host JDK 24 vs build target Java 21 → Surefire/Failsafe need `-Dnet.bytebuddy.experimental=true`; ArchUnit warnings on JDK 24 class files [R: environment memory]
- No Kubernetes manifests, Helm, Terraform; secrets only via `.env` file; no Vault/SealedSecrets
