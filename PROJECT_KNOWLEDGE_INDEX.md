# SmartHire — PROJECT_KNOWLEDGE_INDEX.md

Phase 0 discovery — project knowledge index for SmartHire development environment.
Generated from Phase 0 discovery session.

---

## 1. Authoritative Sources by Topic

| Topic | Authoritative File(s) | Path | Status |
|-------|----------------------|------|--------|
| Project purpose and tech stack | PROJECT_CONTEXT.md | Root | Current [V] |
| Project rules and workflow | CLAUDE.md | Root | Current [V] |
| Agent instructions | AGENTS.md | Root | Current [V] |
| Phase status and blockers | PROJECT_STATUS.md | Root | Current [V] |
| Persistent project memory | PROJECT_MEMORY.md | Root | Current [V] |
| Project overview | README.md | Root | Current [V] |
| Phase 2 backend integration | phase2_report.md | Root | Claude-owned [R] |
| Backend integration gaps | backend_integration_gaps_summary.md | Root | Current [V] |
| Design system specification | docs/design.md | docs/ | Current [V] |
| Requirements traceability | docs/traceability-matrix.md | docs/ | Current [V] |
| Architecture decisions | docs/architecture/adrs.md | docs/architecture/ | Current [V] |
| Phase 3 technical architecture | docs/architecture/phase-3-technical-architecture.md | docs/architecture/ | Current [V] |
| Phase 2 product model | docs/discovery/phase-2-product-model.md | docs/discovery/ | Current [V] |
| Source requirements | docs/discovery/project-44-source-requirements.md | docs/discovery/ | Current [V] |
| Repository assessment | docs/discovery/repository-assessment.md | docs/discovery/ | Current [V] |
| Requirements register | docs/discovery/requirements-register.md | docs/discovery/ | Current [V] |
| Phase 4 engineering governance | docs/governance/phase-4-engineering-governance.md | docs/governance/ | Current [V] |
| Phase 6 AI development strategy | docs/governance/phase-6-ai-development-strategy.md | docs/governance/ | Current [V] |
| Phase 5 implementation roadmap | docs/roadmap/phase-5-implementation-roadmap.md | docs/roadmap/ | Current [V] |
| Skill lock file | skills-lock.json | Root | Current [V] |
| Maven configuration | pom.xml | Root | Current [V] |
| Frontend configuration | frontend/package.json | frontend/ | Current [V] |
| CI pipeline | .github/workflows/ci.yml | .github/ | Current [V] |
| Docker Compose stack | deploy/docker-compose.yml | deploy/ | Current [V] |
| Environment template | deploy/.env.example | deploy/ | Current [V] |
| Kilo configuration | .kilo/kilo.jsonc | .kilo/ | Current [V] |
| Kilo agent manager | .kilo/agent-manager.json | .kilo/ | Current [V] |

---

## 2. Stale or Contradictory Records

| Record | Location | Issue | Resolution | Status |
|--------|----------|-------|------------|--------|
| `openapi_missing` decision | PROJECT_STATUS.md / corrections.md | Stale — OpenAPI annotations now present in services | Corrected in corrections.md | Resolved [V] |
| `ai-service missing` decision | PROJECT_STATUS.md / corrections.md | Stale — AI service implemented and tested | Corrected in corrections.md | Resolved [V] |
| `document_notification_audit_missing` | PROJECT_STATUS.md | Stale — services implemented | N/A | Stale [R] |
| `observability_missing` | PROJECT_STATUS.md | No Micrometer/Prometheus, no distributed tracing | N/A | Open gap |
| `cloud_deployment_missing` | PROJECT_STATUS.md | No Kubernetes/Helm/Terraform manifests | N/A | Out of scope (Inc 8) |
| `frontend.candidate_interview_display_gap` | PROJECT_STATUS.md / corrections.md | Candidate pages omit interview fields | Documented constraint | Open gap |
| `api.candidate_access_interview_fields` | PROJECT_STATUS.md | API returns fields, UI doesn't display | N/A | Documented [V] |

---

## 3. Session Memory Records

| File | Path | Date | Size | Content Type |
|------|------|------|------|-------------|
| Session memory log | `.remember/logs/memory-2026-09-17.log` | 2026-09-17 | 11KB | Session memory |
| Session memory log | `.remember/logs/memory-2026-09-18.log` | 2026-09-18 | 14KB | Session memory |
| Session memory log | `.remember/logs/memory-2026-10-16.log` | 2026-10-16 | 28KB | Session memory |
| Session memory log | `.remember/logs/memory-2026-10-17.log` | 2026-10-17 | 8KB | Session memory |
| Hook errors log | `.remember/logs/hook-errors.log` | 2026-10-17 | 3KB | Error records |
| Save logs (autonomous) | `.remember/logs/autonomous/save-*.log` | Sep-Oct 2026 | 36 bytes each | Save markers |
| Session-end logs | `.remember/logs/session-end-*.log` | Sep-Oct 2026 | 37 bytes each | Session termination |

---

## 4. Key Verified Facts

| Fact | Evidence | Confidence |
|------|----------|------------|
| Backend suites pass across 10 modules | `mvn test` output; auth 77, job 81, application 137, recruitment 122, ai 52, document+notification+common 44 | Verified [V] |
| 69 frontend unit tests across 11 files | `npm run test:run` output | Verified [V] |
| All 10 Maven modules compile | `mvn compile` output | Verified [V] |
| Spotless passes on 203 files | `mvn spotless:check` output | Verified [V] |
| SpotBugs passes with 0 bugs | `mvn spotbugs:check` output | Verified [V] |
| Host JDK 24 vs Java 21 build target | `pom.xml`, AGENTS.md constraints | Verified [V] |
| Docker/Testcontainers unavailable locally | AGENTS.md constraints | Verified [V] |
| AI service excluded from CI verify | ci.yml, AGENTS.md constraints | Verified [V] |
| 9 microservices + common library | pom.xml modules | Verified [V] |
| PostgreSQL 16, Redis 7 | docker-compose.yml | Verified [V] |
| Eureka service discovery | docker-compose.yml, ADR-0005 | Verified [V] |
| Spring Cloud Gateway | docker-compose.yml, ADR-0004 | Verified [V] |
| OpenFeign inter-service clients | PROJECT_CONTEXT.md | Verified [V] |
| Resilience4j fault tolerance | PROJECT_CONTEXT.md, phase2_report.md | Verified [V] |
| springdoc-openapi (Inc 5) | PROJECT_CONTEXT.md | Verified [V] |
| RFC 9457 Problem Details error style | PROJECT_CONTEXT.md | Verified [V] |
| UUID primary keys for cross-service opacity | PROJECT_CONTEXT.md | Verified [V] |
| 28 skills locked in skills-lock.json | skills-lock.json | Verified [V] |
| Stitch design system with 33+ screens | stitch_smarthire_recruitment_design_system/ | Verified [V] |

---

## 5. Known Gaps and Constraints

| Gap | Impact | Source | Status |
|-----|--------|--------|--------|
| Candidate interview details missing from UI | Candidate pages don't show interviewDate, interviewStartTime, interviewer, interviewLocation | PROJECT_STATUS.md corrections | Open |
| Recruiter application list unsupported | Lookup by ID only, no list view | PROJECT_CONTEXT.md | Open |
| No password reset endpoint | Candidate workflow gap | PROJECT_CONTEXT.md | Open |
| No profile-edit endpoint | Candidate/recruiter/admin workflow gap | PROJECT_CONTEXT.md | Open |
| AI features out of scope for frontend | No AI UI integration | PROJECT_CONTEXT.md | Out of scope |
| No observability (Micrometer/Prometheus) | No metrics, tracing, or log aggregation | PROJECT_STATUS.md | Open gap |
| No cloud deployment manifests | No K8s/Helm/Terraform | PROJECT_STATUS.md | Out of scope (Inc 8) |
| No Vault/SealedSecrets | Secrets only via .env file | PROJECT_STATUS.md | Out of scope (Inc 8) |
| Docker/Testcontainers unavailable locally | No integration tests locally | AGENTS.md constraints | Blocker |
| PostgreSQL credentials unknown | Cannot run DB-dependent tests | AGENTS.md constraints | Blocker (user approval) |
| Host JDK 24 vs Java 21 target | Surefire needs `-Dnet.bytebuddy.experimental=true` | AGENTS.md constraints | Workaround applied |
| SpotBugs skipped locally | Class file version 68 | AGENTS.md constraints | Workaround applied |
| AI service excluded from CI verify | Compilation/test infra issues | AGENTS.md constraints | Workaround applied |

---

## 6. File Locations Reference

### Root-level files
- `AGENTS.md` — Kilo Code agent instructions
- `CLAUDE.md` — Project rules and development workflow
- `PROJECT_CONTEXT.md` — Technology stack, architecture, conventions
- `PROJECT_STATUS.md` — Phase status, verification results, blockers
- `PROJECT_MEMORY.md` — Persistent project memory
- `README.md` — Project overview, Review-1 demo setup
- `phase2_report.md` — Phase 2 backend integration report (Claude-owned)
- `backend_integration_gaps_summary.md` — AI service security + integration gaps
- `skills-lock.json` — Skill lock file (28 skills)
- `pom.xml` — Maven parent POM (10 modules)
- `deploy/docker-compose.yml` — Docker Compose stack
- `deploy/.env.example` — Environment variable template
- `.github/workflows/ci.yml` — GitHub Actions CI pipeline
- `.kilo/kilo.jsonc` — Kilo configuration
- `.kilo/agent-manager.json` — Kilo session manager

### Frontend files
- `frontend/package.json` — Frontend dependencies and scripts
- `frontend/tailwind.config.js` — Tailwind design tokens
- `frontend/src/api/client.ts` — API client
- `frontend/src/components/ui/` — 21 UI components
- `frontend/src/pages/` — 37 pages across 5 groups
- `frontend/src/__tests__/` — 6 Vitest test files

### Backend service files (each service)
- `services/<service>/pom.xml` — Module POM
- `services/<service>/src/main/java/` — Java source
- `services/<service>/src/main/resources/application.yml` — Service config
- `services/<service>/src/main/resources/db/migration/` — Flyway migrations
- `services/<service>/src/test/java/` — Unit tests

### Documentation files
- `docs/design.md` — Design system specification
- `docs/traceability-matrix.md` — Requirements traceability
- `docs/architecture/adrs.md` — Architecture Decision Records
- `docs/architecture/phase-3-technical-architecture.md` — Phase 3 architecture
- `docs/discovery/` — Phase 2 product model, requirements, assessment
- `docs/governance/` — Phase 4 governance, Phase 6 AI strategy
- `docs/roadmap/` — Phase 5 implementation roadmap

### Design system files
- `stitch_smarthire_recruitment_design_system/design.md` — Design specification
- `stitch_smarthire_recruitment_design_system/*/code.html` — Per-screen HTML/CSS
- `stitch_smarthire_recruitment_design_system/*/screen.png` — Per-screen screenshots

### Tool configuration files
- `.continue/agents/new-config.yaml` — Continue.dev example config
- `.continue/agents/new-config-1.yaml` — Continue.dev example config
- `.continue/agents/new-config-2.yaml` — Continue.dev example config
- `.vscode/settings.json` — VS Code settings
- `.kilo/kilo.jsonc` — Kilo configuration
- `.kilo/agent-manager.json` — Kilo session manager

### Session memory files
- `.remember/logs/memory-*.log` — Session memory logs
- `.remember/logs/session-end-*.log` — Session termination records
- `.remember/logs/autonomous/save-*.log` — Autonomous save records
- `.remember/tmp/` — Temporary session state

---

## 7. Recommendations for Future Agents

1. **Always read** `AGENTS.md`, `CLAUDE.md`, `PROJECT_CONTEXT.md`, and `PROJECT_STATUS.md` before making changes.
2. **Check git status** before editing to understand current working tree state.
3. **Use verified build/test commands** from AGENTS.md; rerun checks affected by changes.
4. **Run broader checks** (Spotless, tests, `mvn verify`) at phase boundaries.
5. **Preserve all user changes** and untracked files.
6. **Never expose secrets** or commit credentials; never read `.env` file contents.
7. **Never use destructive Git operations** (no reset, clean, stash, revert, commit, push, branch switches, history rewrites).
8. **Never claim tests passed** unless they were actually run.
9. **Update PROJECT_STATUS.md** after substantial tasks.
10. **Keep context concise**; do not duplicate information across multiple files.
