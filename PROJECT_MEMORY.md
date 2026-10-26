# SmartHire — PROJECT MEMORY

**Purpose:** Persistent project memory so any agent can quickly resume work without re-reading the entire codebase.

**Location:** `PROJECT_MEMORY.md` (repository root)
**Source of truth:** `AGENTS.md`, `CLAUDE.md`, `PROJECT_CONTEXT.md`, `PROJECT_STATUS.md`, `README.md`, `frontend/README.md`, `backend_integration_gaps_summary.md`

---

## 1. Project Overview

| Field | Value |
|-------|-------|
| **Project name** | SmartHire — Online Job Recruitment System (Project 44) |
| **Course** | 24SDCS03A/R — SOA Programming and Microservices (AY 2026-27) |
| **Repo** | https://github.com/Hareesh-Reddy-9126/PS044-SmartHire.git |
| **Branch** | `main` (sole long-term branch; origin/main in sync) |
| **Status** | Phase 4 (Quality/Security/CI) complete; Phase 8 (frontend redesign + verification) complete; role-aware candidate/recruiter/administrator website complete. |
| **Total tests** | Verified: auth 77, job 81, application 137, recruitment 122, document+notification+common 44, ai 52; frontend 69 — all passing |
| **Runtime E2E** | Verified via Docker Compose stack; all workflows PASS after `RecruitmentService.shortlist()` fix |

## 2. Technology Stack

### Backend (Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0 Moorgate)

| Module | Directory | Port | Responsibility | DB Schema |
|--------|-----------|------|----------------|-----------|
| service-registry | `services/service-registry` | 8761 | Eureka service discovery | — |
| api-gateway | `services/api-gateway` | 8080 | Spring Cloud Gateway, edge auth, rate limiting | — |
| auth-service | `services/auth-service` | 8082 | JWT issuance + JWKS, Redis refresh-token store | `auth_svc` |
| job-service | `services/job-service` | 8081 | Job aggregate (create/read/update/open/close/reopen) | `job_svc` |
| application-service | `services/application-service` | 8083 | Applications, apply/withdraw/status update | `application_svc` |
| recruitment-service | `services/recruitment-service` | 8084 | Recruitment workflow, interview scheduling, final status | `recruitment_svc` |
| document-service | `services/document-service` | 8085 | Document management (upload/download/delete) | `document_svc` |
| notification-service | `services/document-service` | 8086 | Notifications (logging sender) | `notification_svc` |
| ai-service | `services/ai-service` | 8087 | AI matching (mock provider + kill switch) | `ai_svc` |
| common | `common/` | — | Shared library (correlation ID filter, exceptions, DTOs) | — |

### Frontend (React 18, TypeScript 5, Vite 5, Tailwind CSS)

| Field | Value |
|-------|-------|
| **Directory** | `frontend/` |
| **Dev server** | `npm run dev` → `http://localhost:5173` |
| **Proxy** | `/api`, `/oauth2`, `/actuator`, `/v3` → `http://localhost:8080` |
| **Build** | `npm run build` → `dist/` |
| **Typecheck** | `npm run typecheck` |
| **Lint** | `npm run lint` (zero warnings) |
| **Test** | `npm run test:run` (Vitest, 69 tests, 11 files) |
| **Auth** | Access tokens in memory only; refresh token HttpOnly cookie; proactive refresh via `AuthContext`; shared role selector on sign-in and registration |

### Infrastructure

- **Database:** PostgreSQL 16 (shared instance, per-service schemas via Flyway)
- **Cache:** Redis 7 (refresh-token store + JWT jti denylist)
- **Container:** Docker Compose (`deploy/docker-compose.yml`, stack `smarthire`)
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) — test, spotless, spotbugs, security-scan
- **API docs:** springdoc-openapi (Inc 5) on services
- **Error style:** RFC 9457 Problem Details, `X-Correlation-Id` header on every response

## 3. Verified Build & Test Commands

| Command | Purpose | Notes |
|---------|---------|-------|
| `mvn -pl services/<module> compile` | Compile one module | |
| `mvn -pl services/<module> test` | Test one module | e.g. application-service → 125 tests |
| `mvn -B verify -pl '!services/ai-service' -am` | Full quality gate (CI) | Excludes ai-service |
| `mvn spotless:check` | Java formatting check | Passes on 203 files |
| `cd frontend && npm run lint` | Frontend ESLint | Zero warnings |
| `cd frontend && npm run typecheck` | TypeScript typecheck | Zero errors |
| `cd frontend && npm run test:run` | Frontend Vitest | 69/69 pass |
| `cd frontend && npm run build` | Production bundle | Must exit 0 |
| `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build` | Full stack | Requires `deploy/.env` |

**Local constraints:**
- Host JDK 24 vs Java 21 build target → Surefire/Failsafe need `-Dnet.bytebuddy.experimental=true`
- SpotBugs skipped locally (class file version 68)
- Docker/Testcontainers unavailable locally → no integration tests locally
- `services/ai-service` excluded from CI verify; its tests need PostgreSQL

## 4. Frontend Routes & Roles

### Public Routes
| Route | Component | Status | API |
|-------|-----------|--------|-----|
| `/` | LandingPage.tsx | Implemented | None (static, three role entry points) |
| `/jobs` | JobBrowsePage.tsx | Implemented | GET `/api/v1/jobs` |
| `/jobs/:jobId` | JobDetailPage.tsx | Implemented | GET `/api/v1/jobs/:id` |
| `/login` | LoginPage.tsx | Implemented | POST `/api/v1/auth/login` (role selector is intent only) |
| `/register` | RegisterPage.tsx | Implemented | POST `/api/v1/auth/register` (role-aware) |

### CANDIDATE Routes
| Route | Component | Status | API |
|-------|-----------|--------|-----|
| `/candidate/applications` | CandidateApplicationsPage.tsx | Implemented | GET `/api/v1/applications` |
| `/candidate/applications/:id` | ApplicationDetailPage.tsx | Implemented | GET `/api/v1/applications/:id` |
| `/candidate/applications/:id/progress` | ProgressPage.tsx | Implemented | — |
| `/candidate/progress/:id` | ProgressDetailPage.tsx | Implemented | — (omits interview fields despite API returning them) |
| `/candidate/documents` | DocumentsPage.tsx | Implemented | GET/POST/DELETE `/api/v1/documents` |
| `/candidate/notifications` | NotificationsPage.tsx | Implemented | GET `/api/v1/notifications` |
| `/candidate/notifications/:id` | NotificationDetailPage.tsx | Implemented | GET `/api/v1/notifications/:id` |
| `/candidate/profile` | CandidateProfilePage.tsx | Implemented | — |
| `/jobs/:jobId/apply` | ApplicationSubmitPage.tsx | Implemented | POST `/api/v1/applications` |

### RECRUITER Routes
| Route | Component | Status | API |
|-------|-----------|--------|-----|
| `/recruiter/jobs` | RecruiterJobsPage.tsx | Implemented | GET `/api/v1/jobs?mine=true` (organization-scoped) |
| `/recruiter/jobs/:id` | RecruiterJobDetailPage.tsx | Implemented | GET `/api/v1/jobs/:id` |
| `/recruiter/jobs/new` | JobCreatePage.tsx | Implemented | POST `/api/v1/jobs` |
| `/recruiter/jobs/:id/edit` | JobEditPage.tsx | Implemented | PUT `/api/v1/jobs/:id` |
| `/recruiter/recruitments` | RecruiterRecruitmentsPage.tsx | Implemented | GET `/api/v1/recruitments` |
| `/recruiter/recruitments/:id` | RecruiterRecruitmentDetailPage.tsx | Implemented | GET/PATCH `/api/v1/recruitments/:id` |
| `/recruiter/applications` | RecruiterApplicationLookupPage.tsx | Implemented | Lookup by ID only (no backend list endpoint) |
| `/recruiter/profile` | RecruiterProfilePage.tsx | Implemented | — |

### ADMIN Routes
| Route | Component | Status | API |
|-------|-----------|--------|-----|
| `/admin/overview` | AdminOverviewPage.tsx | Implemented | — (recruitment count only) |
| `/admin/recruitments` | AdminRecruitmentsPage.tsx | Implemented | — |
| `/admin/lookup` | AdminLookupPage.tsx | Implemented | — |
| `/admin/profile` | AdminProfilePage.tsx | Implemented | — |

### Error Pages
`/403`, `/404`, `/error`, `/offline`, `/session-expired`, `/workspace`

### Known Frontend Gaps
1. `ProgressDetailPage.tsx` and `ApplicationDetailPage.tsx` omit interview fields despite API returning them
2. Recruiter application list unsupported by backend (lookup by ID only)
3. No password reset or profile-edit backend endpoints
4. AI features out of scope for frontend
5. `frontend/src/assets/` is empty (no image assets)
6. Former `/company/*` pages were removed; company management is not exposed by the current client

## 5. API Endpoints (via Gateway `/api/v1/*`)

| Prefix | Operations |
|--------|-----------|
| `/api/v1/auth/*` | login, role-aware register (candidate / recruiter / invited administrator), refresh, logout, me, admin/whoami |
| `/api/v1/jobs/*` | list (paginated, filterable; `mine=true` scopes a recruiter to their organization), get, create, update, open/close/reopen |
| `/api/v1/applications/*` | list (candidate-scoped), get, create (apply), withdraw, status update (recruiter) |
| `/api/v1/recruitments/*` | list, get, create, schedule interview, update interview status, set final status |
| `/api/v1/documents/*` | list, upload, download (redirect), delete |
| `/api/v1/notifications/*` | list, get single |

Every response carries `X-Correlation-Id` header. Errors use RFC 9457 Problem Details.

## 6. Key Coding Conventions (Java)

| Convention | Detail |
|------------|--------|
| Java version | 21 bytecode/language level (regardless of host JVM) |
| Compiler flag | `-parameters` for Jackson record deserialization (JEP 118) |
| Surefire/Failsafe argLine | `-Dnet.bytebuddy.experimental=true` (for JDK 24 host) |
| DTOs | `RegisterRequest`/`LoginRequest` use immutable classes with `@JsonCreator`/`@JsonProperty` (not records) so validation annotations and role-specific optional fields stay explicit; Jackson uses standard Spring Boot auto-configuration |
| Formatting | Spotless + Google Java Format 1.22.0, `removeUnusedImports`, `trimTrailingWhitespace`, `endWithNewline` |
| Static analysis | SpotBugs `effort=Max`, `threshold=Medium`, `failOnError=true`; `@SuppressFBWarnings` annotations (provided scope) |
| ArchUnit | Architecture rules enforced in tests |
| Secrets | Never in source/committed config; runtime from environment; `deploy/.env` git-ignored |
| API style | REST, RFC 9457 Problem Details, correlation-id header, UUID primary keys |

## 7. Known Outstanding Work / Gaps

### Backend
| # | Issue | Location | Priority |
|---|-------|----------|----------|
| 1 | No password reset backend endpoints | auth-service | Low |
| 2 | No profile-edit backend endpoints | auth-service | Low |
| 3 | Recruiter application list unsupported (lookup by ID only) | application-service | Medium |
| 4 | Admin overview shows only recruitment count, not full analytics | recruitment-service | Low |
| 5 | `ai-service` tests need PostgreSQL (excluded from CI) | ai-service | Low |

### Frontend
| # | Issue | Location | Priority |
|---|-------|----------|----------|
| 1 | ProgressDetailPage omits interview fields | `frontend/src/pages/candidate/ProgressDetailPage.tsx` | Medium |
| 2 | ApplicationDetailPage omits interview fields | `frontend/src/pages/candidate/ApplicationDetailPage.tsx` | Medium |
| 3 | `frontend/src/assets/` empty — no images | `frontend/src/assets/` | Low |
| 4 | No design system npm package (assets local only) | `stitch_smarthire_recruitment_design_system/` | Low |

### Infrastructure
| # | Issue | Detail |
|---|-------|--------|
| 1 | No Kubernetes, Helm, or Terraform manifests | Deferred to Phase 6 |
| 2 | No browser automation (Playwright/Selenium) | Manual E2E only |
| 3 | No accessibility auditing (axe-core) | Manual review |
| 4 | No Lighthouse CI | No automated performance monitoring |

## 8. Recent Changes Summary (Phase 8)

### Phase 8A — Frontend Redesign (COMPLETE)
- Redesigned `LandingPage.tsx` hero section with premium typography, refined capability cards
- Simplified `JobBrowsePage.tsx` architecture, cleaned up unused state
- Added new UI components: Alert, Badge, Breadcrumbs, CopyableId, DefinitionList, EmptyState, PageHeader, Pagination, Select, Skeleton, Spinner, Table, Tabs, Textarea, Timeline
- Barrel export `src/components/ui/index.ts`
- Design system from `stitch_smarthire_recruitment_design_system/` (22 design entries)

### Phase 8B — Auth & Workflow Verification (COMPLETE)
- Auth-service unit suite currently at 77 tests (was 55 at Phase 8B)
- Frontend suite currently at 69 tests across 11 files
- HTTP 400 on registration = expected validation (password ≥12 chars, email format)
- RECRUITER/ADMIN can now self-register; administrator signup requires `AUTH_ADMIN_SIGNUP_CODE`

### Phase 8C — Runtime E2E Verification (COMPLETE)
- Docker Compose stack verified: all 8 services healthy
- All E2E workflows PASS after fix

### Phase 8D — Recruitment final-status 503 Fix (COMPLETE)
**Root cause:** `RecruitmentService.shortlist()` created recruitment record but never transitioned application from `APPLIED` to `SHORTLISTED`. When `updateFinalStatus()` called `synchronizeApplicationStatus()` with `HIRED`, `application.hire()` required `SHORTLISTED` but found `APPLIED`, throwing `IllegalApplicationStateException`. Resilience4j circuit breaker opened → 503.

**Fix:** Added `applicationClient.updateStatus(applicationId, "SHORTLISTED")` in `RecruitmentService.shortlist()` after creating recruitment record.

**File changed:** `services/recruitment-service/src/main/java/com/smarthire/recruitment/service/RecruitmentService.java`

### Phase 8E — Role-aware Website & Authorization Hardening (COMPLETE)
- One public website now serves all three roles. `/login` and `/register` share `RoleSelector`; the choice is stored in `?role=` and preserved across auth links.
- Candidate and recruiter self-registration are open. Recruiter registration requires an organization name and creates a dedicated `Organization`, so job ownership works immediately.
- Administrator self-registration requires the deployment's `AUTH_ADMIN_SIGNUP_CODE` (constant-time comparison; blank disables signup; never logged or returned).
- Registration returns only a userId; the client hands off to the matching login portal. The JWT/server role remains authoritative.
- An explicit `?role=` mismatch at login opens the account's real workspace with a dismissible notice; a generic `/login` never shows it. `next` redirects are same-origin and role-checked.
- Landing page presents one workspace entry point per role while keeping public job browsing.
- Recruiter "My jobs" uses the server-side `mine=true` filter. A candidate requesting `mine=true` gets 403.
- **Critical fix:** `@EnableMethodSecurity(securedEnabled = true)` in auth-service, because `@Secured` admin restrictions on company endpoints were previously inert.
- Company endpoints hardened: list ADMIN-only, read ADMIN or active member, register RECRUITER, membership management requires active `COMPANY_ADMIN`; 404/403 mappings added.
- `V4__company_membership_invitation_optional.sql` makes `company_memberships.invited_at` nullable (self-requested membership has no inviter).
- Removed the five unused `/company/*` pages and their client APIs; backend company endpoints remain, hardened.
- Verified: auth-service 77 tests, job-service 81 tests, frontend 69 tests / 11 files, live API via gateway, and six browser workflows via Playwright.

## 9. Git State (as of session)

**Branch:** `main`. Work is committed locally with a clean working tree; verify with `git status` and `git rev-parse HEAD` before starting new work.

**Do not assume** a clean tree from this note alone — always inspect `git status` first. Scratch files, screenshots, and browser scripts belong outside the repository (temporary workspace) and must not be committed.

## 10. Key Files to Read First (for new agents)

1. `AGENTS.md` — Project instructions for Kilo Code agents
2. `CLAUDE.md` — Project rules and development workflow
3. `PROJECT_CONTEXT.md` — Architecture and navigation reference
4. `PROJECT_STATUS.md` — Current project position and outstanding work
5. `README.md` — Full project overview, Postman E2E flow
6. `frontend/README.md` — Frontend stack, scripts, architecture, API surface, roles
7. `backend_integration_gaps_summary.md` — AI service security config + gap summary
8. `deploy/docker-compose.yml` — Local demonstration stack
9. `deploy/.env.example` — Environment variable template (keys only)

## 11. User-Side Actions Required (before full publish)

| Action | Reason |
|--------|--------|
| Provide PostgreSQL credentials (`AUTH_SVC_DB_PASSWORD`) | Required for DB-dependent integration tests |
| Provide actual API keys for `.continue/agents/*.yaml` | Required for Continue.dev integration |
| Keep Playwright/browser scripts outside the repository | Browser E2E was run ad hoc; it is not a repo dependency |
| Install accessibility auditing (axe-core) | Required for automated WCAG compliance testing |

## 12. How to Use This Memory

When starting a new chat session:
1. Read this `PROJECT_MEMORY.md` first (5 min vs 60+ min full re-read)
2. Read `AGENTS.md` and `CLAUDE.md` for current rules
3. Check `git status` for any uncommitted changes
4. Proceed with the specific task

**Do NOT overwrite** `phase2_report.md` — it is owned by Claude for ongoing Phase 2 verification.

*Last updated: 2026-10-26. Keep this file in sync with implementation changes.*
