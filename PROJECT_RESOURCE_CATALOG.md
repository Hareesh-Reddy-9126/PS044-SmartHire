# SmartHire — PROJECT_RESOURCE_CATALOG.md

Phase 0 discovery — complete resource inventory for the SmartHire development environment.
Generated from Phase 0 discovery session. All paths are relative to the repository root.

---

## 1. Repository Root

| Item | Value |
|------|-------|
| Root path | Repository root |
| Git branch | `main` (sole long-term branch; `origin/main` in sync) |
| Last commit | `e909012 docs: enhance README with 3D elements, animations, and top repo design inspiration` |
| Working tree | Extensive modifications (staged + unstaged + untracked) — see git status output |

---

## 2. Directory Inventory

### Root-level directories

| Directory | Purpose | Key Contents | Relationship to SmartHire |
|-----------|---------|--------------|---------------------------|
| `.agents/` | Kilo Code skill definitions | 398+ skill subdirectories (symlinked from `.claude/skills`) | Agent skill library for code generation, testing, evaluation |
| `.claude/` | Claude Code skill definitions | `skills/` directory with skill manifests | Agent skill library (symlinked to `.agents/skills`) |
| `.continue/` | Continue.dev agent configs | `agents/new-config.yaml`, `new-config-1.yaml`, `new-config-2.yaml` | Example LLM provider configurations (not active) |
| `.freebuff/` | Project identification | `project-id` file (37 bytes) | Project marker file |
| `.github/` | CI/CD and automation | `workflows/ci.yml`, `java-upgrade/` logs | GitHub Actions CI pipeline |
| `.git/` | Git repository | Git internals | Version control |
| `.kilo/` | Kilo Code config | `kilo.jsonc`, `agent-manager.json`, `plans/` | Kilo session management |
| `.postman/` | Postman workspace config | `globals/workspace.globals.yaml` | Postman workspace globals |
| `.remember/` | Session memory | `logs/` directory with memory/session-end logs | Autonomous session persistence |
| `.vscode/` | VS Code settings | `settings.json` | Editor configuration |
| `common/` | Shared Java library | `src/main/java`, `src/test/java` | Correlation-ID filter, RFC 9457 Problem Details, shared DTOs |
| `deploy/` | Deployment configuration | `docker-compose.yml`, `.env`, `.env.example`, `postman/` | Docker Compose stack, Postman E2E collection |
| `docs/` | Project documentation | `design.md`, `traceability-matrix.md`, `architecture/`, `discovery/`, `governance/`, `roadmap/` | Architecture docs, ADRs, phase documentation |
| `frontend/` | React SPA | `src/`, `public/`, `dist/`, `package.json`, `tailwind.config.js` | Frontend application |
| `node_modules/` | Frontend dependencies | Installed npm packages | Frontend build tooling |
| `postman/` | Postman workspace | `globals/` directory | Postman workspace configuration |
| `services/` | Microservices | 9 service directories (`service-registry`, `api-gateway`, `auth-service`, `job-service`, `application-service`, `recruitment-service`, `document-service`, `notification-service`, `ai-service`) | Backend microservices |
| `stitch_smarthire_recruitment_design_system/` | UI design assets | `design.md`, per-screen `code.html` + `screen.png` | Design system reference for frontend UI |
| `target/` | Build output | Java compilation artifacts | Maven build output |

### Frontend directory structure

| Path | Purpose |
|------|---------|
| `frontend/src/App.tsx` | Root React component |
| `frontend/src/main.tsx` | Application entry point |
| `frontend/src/api/client.ts` | API client with all service endpoints |
| `frontend/src/components/layout/Layout.tsx` | Page layout wrapper |
| `frontend/src/components/ui/` | 21 shadcn-style UI components (Alert, Badge, Button, Card, etc.) |
| `frontend/src/context/` | AuthContext, ToastContext |
| `frontend/src/hooks/useApi.ts` | API hook |
| `frontend/src/pages/admin/` | 4 admin pages (Lookup, Overview, Profile, Recruitments) |
| `frontend/src/pages/auth/` | LoginPage, RegisterPage |
| `frontend/src/pages/candidate/` | 9 candidate pages |
| `frontend/src/pages/public/` | LandingPage, JobBrowsePage, JobDetailPage |
| `frontend/src/pages/recruiter/` | 9 recruiter pages |
| `frontend/src/pages/shared/` | ErrorPages, ProfilePage |
| `frontend/src/routes/AppRoutes.tsx` | Route definitions |
| `frontend/src/styles/index.css` | Tailwind CSS + custom design tokens |
| `frontend/src/utils/errors.ts` | RFC 9457 error handling |
| `frontend/src/utils/format.ts` | Date/time formatting utilities |
| `frontend/src/__tests__/` | 6 Vitest test files |

### Backend services directory structure

| Module | Directory | Port | Responsibility |
|--------|-----------|------|----------------|
| service-registry | `services/service-registry` | 8761 | Eureka service discovery |
| api-gateway | `services/api-gateway` | 8080 | Spring Cloud Gateway, edge auth, rate limiting |
| auth-service | `services/auth-service` | 8082 | JWT issuance + JWKS, Redis refresh-token store |
| job-service | `services/job-service` | 8081 | Job aggregate |
| application-service | `services/application-service` | 8083 | Applications, Feign calls to job/notification |
| recruitment-service | `services/recruitment-service` | 8084 | Recruitment workflow, interview scheduling |
| document-service | `services/document-service` | 8085 | Document management |
| notification-service | `services/notification-service` | 8086 | Application status notifications |
| ai-service | `services/ai-service` | — | AI matching/evaluation |
| common | `common/` | — | Shared library |

Each service has: `pom.xml`, `src/main/java`, `src/main/resources/application.yml`, `src/main/resources/db/migration/` (Flyway), `src/test/java`.

---

## 3. Key Configuration Files

| File | Purpose | Contents |
|------|---------|----------|
| `pom.xml` | Maven parent POM | Spring Boot 3.4.1, Spring Cloud 2024.0.0, Java 21, 10 modules |
| `frontend/package.json` | Frontend dependencies/scripts | React 18, TypeScript 5, Vite 5, Tailwind CSS, Vitest, ESLint |
| `frontend/tailwind.config.js` | Tailwind design tokens | Custom color palette, typography, spacing |
| `.kilo/kilo.jsonc` | Kilo configuration | Minimal schema reference |
| `.kilo/agent-manager.json` | Kilo session manager | Session tracking, active target |
| `.github/workflows/ci.yml` | GitHub Actions CI | Test, Spotless, SpotBugs, OWASP Dependency-Check jobs |
| `deploy/docker-compose.yml` | Docker Compose stack | 9 services + PostgreSQL + Redis + Eureka |
| `deploy/.env.example` | Environment template | Keys only (no values) |
| `skills-lock.json` | Skill lock file | 28 skills with sources and hashes |

---

## 4. Skills Catalogue (28 skills)

| Skill | Source | Path | Purpose | SmartHire Use Case |
|-------|--------|------|---------|-------------------|
| academy-guide | anthropics/skills | `.claude/skills/academy-guide/SKILL.md` | Claude Academy course recommendations | Learning resources |
| accessibility | addyosmani/web-quality-skills | `.claude/skills/accessibility/SKILL.md` | WCAG 2.2 accessibility audit | Frontend a11y compliance |
| algorithmic-art | anthropics/skills | `.claude/skills/algorithmic-art/SKILL.md` | p5.js generative art | Not applicable |
| api-security-testing | usestrix/strix | `.claude/skills/api-security-testing/SKILL.md` | Strix API security pentesting | API vulnerability testing |
| application-security-testing | usestrix/strix | `.claude/skills/application-security-testing/SKILL.md` | Full AppSec review | Security assessment |
| brand-guidelines | anthropics/skills | `.claude/skills/brand-guidelines/SKILL.md` | Anthropic brand styling | Documentation styling |
| canvas-design | anthropics/skills | `.claude/skills/canvas-design/SKILL.md` | Visual art creation | Not applicable |
| ci-security-scanning-with-strix | usestrix/strix | `.claude/skills/ci-security-scanning-with-strix/SKILL.md` | Strix CI/CD security scanning | Pipeline security |
| claude-api | anthropics/skills | `.claude/skills/claude-api/SKILL.md` | Claude API reference | LLM integration reference |
| diagram-design | cathrynlavery/diagram-design | `.claude/skills/diagram-design/SKILL.md` | Diagram generation | Architecture diagrams |
| discernment-nudge | anthropics/skills | `.claude/skills/discernment-nudge/SKILL.md` | Follow-up questioning | Decision support |
| doc-coauthoring | anthropics/skills | `.claude/skills/doc-coauthoring/SKILL.md` | Documentation co-authoring | Tech writing |
| docx | anthropics/skills | `.claude/skills/docx/SKILL.md` | Word document manipulation | Not applicable |
| e2e-testing-patterns | wshobson/agents | `.claude/skills/e2e-testing-patterns/SKILL.md` | Playwright/Cypress E2E testing | Not configured (no browser automation) |
| find-security-vulnerabilities-in-code | usestrix/strix | `.claude/skills/find-security-vulnerabilities-in-code/SKILL.md` | Strix white-box security review | Code security audit |
| find-skills | vercel-labs/skills | `.claude/skills/find-skills/SKILL.md` | Skill discovery | Skill management |
| fix-security-vulnerabilities-with-strix | usestrix/strix | `.claude/skills/fix-security-vulnerabilities-with-strix/SKILL.md` | Strix vulnerability remediation | Security fixes |
| frontend-design | anthropics/skills | `.claude/skills/frontend-design/SKILL.md` | Visual design guidance | UI/UX improvements |
| gstack | garrytan/gstack | `.claude/skills/gstack/SKILL.md` | gstack skill suite router | Not applicable |
| harness-creator | walkinglabs/learn-harness-engineering | `.claude/skills/harness-creator/SKILL.md` | Agent harness creation | Agent configuration |
| internal-comms | anthropics/skills | `.claude/skills/internal-comms/SKILL.md` | Internal communications writing | Not applicable |
| java-junit | github/awesome-copilot | `.claude/skills/java-junit/SKILL.md` | JUnit 5 best practices | Backend testing |
| java-springboot | github/awesome-copilot | `.claude/skills/java-springboot/SKILL.md` | Spring Boot best practices | Backend development |
| llm-evaluation | wshobson/agents | `.claude/skills/llm-evaluation/SKILL.md` | LLM evaluation strategies | AI service evaluation |
| llm-security | semgrep/skills | `.claude/skills/llm-security/SKILL.md` | OWASP LLM Top 10 security | AI service security |
| managed-pentesting-with-strix | usestrix/strix | `.claude/skills/managed-pentesting-with-strix/SKILL.md` | Strix managed pentesting | Cloud security testing |
| mcp-builder | anthropics/skills | `.claude/skills/mcp-builder/SKILL.md` | MCP server creation | Not configured |
| owasp-security | hoodini/ai-agents-skills | `.claude/skills/owasp-security/SKILL.md` | OWASP Top 10 secure coding | Security best practices |
| owasp-top-10-testing | usestrix/strix | `.claude/skills/owasp-top-10-testing/SKILL.md` | OWASP Top 10 testing | Security testing |
| pdf | anthropics/skills | `.claude/skills/pdf/SKILL.md` | PDF manipulation | Not applicable |
| penetration-testing-with-strix | usestrix/strix | `.claude/skills/penetration-testing-with-strix/SKILL.md` | Strix penetration testing | Security testing |
| postgresql-best-practices | microsoft/postgres-skills | `.claude/skills/postgresql-best-practices/SKILL.md` | PostgreSQL best practices | Database design |
| postgresql-table-design | wshobson/agents | `.claude/skills/postgresql-table-design/SKILL.md` | PostgreSQL schema design | Database schema |
| pptx | anthropics/skills | `.claude/skills/pptx/SKILL.md` | PowerPoint manipulation | Not applicable |
| prompt-master | nidhinjs/prompt-master | `.claude/skills/prompt-master/SKILL.md` | Prompt engineering | Prompt optimization |
| skill-creator | anthropics/skills | `.claude/skills/skill-creator/SKILL.md` | Skill creation/optimization | Custom skill development |
| slack-gif-creator | anthropics/skills | `.claude/skills/slack-gif-creator/SKILL.md` | Slack GIF creation | Not applicable |
| spring-boot-engineer | jeffallan/claude-skills | `.claude/skills/spring-boot-engineer/SKILL.md` | Spring Boot 3.x code generation | Backend development |
| spring-boot-rest-api-standards | giuseppe-trisciuoglio/developer-kit | `.claude/skills/spring-boot-rest-api-standards/SKILL.md` | REST API standards | API design |
| springboot-security | affaan-m/ecc | `.claude/skills/springboot-security/SKILL.md` | Spring Security best practices | Backend security |
| template-skill | anthropics/skills | `.claude/skills/template-skill/SKILL.md` | Skill template | Not applicable |
| theme-factory | anthropics/skills | `.claude/skills/theme-factory/SKILL.md` | Theme styling | UI theming |
| typescript-pro | jeffallan/claude-skills | `.claude/skills/typescript-pro/SKILL.md` | Advanced TypeScript types | Frontend type safety |
| vercel-react-best-practices | vercel-labs/agent-skills | `.claude/skills/vercel-react-best-practices/SKILL.md` | React performance | Frontend optimization |
| web-app-penetration-testing | usestrix/strix | `.claude/skills/web-app-penetration-testing/SKILL.md` | Strix web app pentesting | Security testing |
| web-artifacts-builder | anthropics/skills | `.claude/skills/web-artifacts-builder/SKILL.md` | Multi-component HTML artifacts | Not applicable |
| web-design-guidelines | vercel-labs/agent-skills | `.claude/skills/web-design-guidelines/SKILL.md` | UI review guidelines | Frontend review |
| webapp-testing | anthropics/skills | `.claude/skills/webapp-testing/SKILL.md` | Playwright testing | Not configured |
| xlsx | anthropics/skills | `.claude/skills/xlsx/SKILL.md` | Spreadsheet manipulation | Not applicable |

**Skill sources:**
- anthropics/skills: 13 skills
- usestrix/strix: 7 skills
- wshobson/agents: 2 skills
- github/awesome-copilot: 2 skills
- jeffallan/claude-skills: 2 skills
- vercel-labs/agent-skills: 2 skills
- Others: cathrynlavery/diagram-design, hoodini/ai-agents-skills, semgrep/skills, nidhinjs/prompt-master, microsoft/postgres-skills, giuseppe-trisciuoglio/developer-kit, affaan-m/ecc, walkinglabs/learn-harness-engineering, garrytan/gstack

---

## 5. Agents and Orchestration

### Kilo Code

| Item | Status | Details |
|------|--------|---------|
| Kilo config | `.kilo/kilo.jsonc` | Minimal schema reference only |
| Agent manager | `.kilo/agent-manager.json` | Active session: `ses_ea60213f4ffeY0cSOKX9S03CEN` |
| Worktrees | Empty (`{}`) | No worktrees configured |
| Sessions | 1 local session tracked | `ses_ea60213f4ffeY0cSOKX9S03CEN` |

### Continue.dev

| Item | Status | Details |
|------|--------|---------|
| Config files | `.continue/agents/new-config.yaml`, `new-config-1.yaml`, `new-config-2.yaml` | Example configurations with placeholder API keys |
| Active | Not configured | No active Continue.dev integration |

### Subagent/delegation capability

Kilo Code supports launching subagents via the `task` tool with types: `explore`, `general`. Subagents run in the current session with fresh context unless `task_id` is resumed.

---

## 6. Plugins, Connectors, and MCP Integrations

| Integration | Provider | Configured | Connected | Auth Valid | Tool Invocation | Notes |
|-------------|----------|------------|-----------|------------|-----------------|-------|
| Continue.dev | continue.dev | Partial (example configs only) | No | No | No | Placeholder configs with `YOUR_OPENAI_API_KEY_HERE` |
| Postman | Postman | Yes | No (manual only) | N/A | Manual | E2E collection at `deploy/postman/SmartHire-Review1-E2E-Collection.json` |
| MCP servers | None configured | No | No | No | No | No MCP server configurations found |
| Docker | Docker Desktop | Yes | Unknown | N/A | Unknown | Not available locally per AGENTS.md constraints |
| PostgreSQL | PostgreSQL 16 | Yes (in compose) | Unknown | No | Unknown | Credentials unknown — must not reconfigure without approval |
| Redis | Redis 7 | Yes (in compose) | Unknown | No | Unknown | Same as above |

---

## 7. Project Knowledge and Persistent Memory

### Project documentation files

| File | Path | Purpose | Status |
|------|------|---------|--------|
| AGENTS.md | Root | Kilo Code agent instructions | Current |
| CLAUDE.md | Root | Project rules and workflow | Current |
| PROJECT_CONTEXT.md | Root | Technology stack, architecture, conventions | Current |
| PROJECT_STATUS.md | Root | Phase status, verification results, blockers | Current (84KB) |
| PROJECT_MEMORY.md | Root | Persistent project memory | Current (256 lines) |
| README.md | Root | Project overview, Review-1 demo setup | Current |
| phase2_report.md | Root | Phase 2 backend integration report | Claude-owned, do not overwrite |
| backend_integration_gaps_summary.md | Root | AI service security config + integration gaps | Current |
| docs/design.md | docs/ | Design system specification | Current |
| docs/traceability-matrix.md | docs/ | Requirements traceability | Current |
| docs/architecture/adrs.md | docs/architecture/ | Architecture Decision Records | Current |
| docs/architecture/phase-3-technical-architecture.md | docs/architecture/ | Phase 3 technical architecture | Current |
| docs/discovery/phase-2-product-model.md | docs/discovery/ | Phase 2 product model | Current |
| docs/discovery/project-44-source-requirements.md | docs/discovery/ | Source requirements | Current |
| docs/discovery/repository-assessment.md | docs/discovery/ | Repository assessment | Current |
| docs/discovery/requirements-register.md | docs/discovery/ | Requirements register | Current |
| docs/governance/phase-4-engineering-governance.md | docs/governance/ | Phase 4 governance | Current |
| docs/governance/phase-6-ai-development-strategy.md | docs/governance/ | Phase 6 AI strategy | Current |
| docs/roadmap/phase-5-implementation-roadmap.md | docs/roadmap/ | Phase 5 roadmap | Current |

### Session memory

| File | Path | Purpose |
|------|------|---------|
| Session logs | `.remember/logs/memory-*.log` | Session memory logs (Sep-Oct 2026) |
| Session-end logs | `.remember/logs/session-end-*.log` | Session termination records |
| Save logs | `.remember/logs/autonomous/save-*.log` | Autonomous save records |

### Stale/contradictory records

| Item | Issue | Resolution |
|------|-------|------------|
| `openapi_missing` decision | Stale — OpenAPI annotations now present in services | Corrected in corrections.md |
| `frontend.candidate_interview_display_gap` | Known gap — candidate pages omit interview fields | Documented constraint |
| `ai-service missing` decision | Stale — AI service implemented and tested | Corrected in corrections.md |

---

## 8. Design, Testing, and Operational Assets

### Design system

| Asset | Path | Description |
|-------|------|-------------|
| Design specification | `stitch_smarthire_recruitment_design_system/design.md` | Full design system with all screen definitions |
| Per-screen code | `stitch_smarthire_recruitment_design_system/*/code.html` | HTML/CSS code for each screen |
| Per-screen screenshots | `stitch_smarthire_recruitment_design_system/*/screen.png` | Visual reference images |
| Calm institutional theme | `stitch_smarthire_recruitment_design_system/calm_institutional/DESIGN.md` | Theme design |

### Testing tools

| Tool | Path | Status | Notes |
|------|------|--------|-------|
| Vitest | `frontend/package.json` | Installed | 49 tests across 8 files |
| Testing Library (React) | `frontend/package.json` | Installed | @testing-library/react + jest-dom |
| JUnit 5 | `pom.xml` | Installed | 538 backend unit tests across 10 modules |
| Testcontainers | Not available | Not installed | Docker/Testcontainers unavailable locally |
| Selenium/Playwright | Not available | Not installed | No browser automation configured |

### Build and quality tools

| Tool | Path | Status | Notes |
|------|------|--------|-------|
| Maven | Root `pom.xml` | Installed | Java 21 target, JDK 24 host |
| Spotless | `pom.xml` | Installed | Google Java Format |
| SpotBugs | `pom.xml` | Installed | Skipped locally (class file version 68) |
| ArchUnit | `pom.xml` | Installed | Warnings on JDK 24 class files |
| ESLint | `frontend/package.json` | Installed | Zero warnings target |
| TypeScript | `frontend/package.json` | Installed | `tsc --noEmit` |
| Vite | `frontend/package.json` | Installed | Build and dev server |

### CI/CD

| Item | Path | Status |
|------|------|--------|
| GitHub Actions | `.github/workflows/ci.yml` | Configured (test, spotless, spotbugs, security-scan) |
| OWASP Dependency-Check | ci.yml security-scan job | Push/main PR only |

### API specifications

| Item | Path | Status |
|------|------|--------|
| OpenAPI/Springdoc | services/*/src/main/resources/application.yml | springdoc-openapi (Inc 5) |
| Postman E2E collection | `deploy/postman/SmartHire-Review1-E2E-Collection.json` | Manual E2E only |
| Postman environment | `deploy/postman/SmartHire-Review1-Environment.json` | Manual E2E only |

---

## 9. Capability and Compatibility Matrix

| Resource | Present in Repo | Discoverable by Kilo | Compatible with Kilo | Configured | Connected/Authenticated | Successfully Invoked | Requires User Action | Not Accessible |
|----------|-----------------|---------------------|---------------------|------------|----------------------|---------------------|---------------------|----------------|
| Backend services (10 modules) | Yes | Yes | Yes | Yes | No (not running) | Partial (compile/test only) | No | No |
| Frontend (React + Vite) | Yes | Yes | Yes | Yes | No (not running) | Partial (lint/typecheck/build) | No | No |
| Docker Compose stack | Yes | Yes | Yes | Yes | No | No | Yes (.env credentials) | No |
| PostgreSQL | Yes (in compose) | Yes | Yes | Yes | No | No | Yes (credentials unknown) | No |
| Redis | Yes (in compose) | Yes | Yes | Yes | No | No | No | No |
| Kilo agent manager | Yes | Yes | Yes | Yes | N/A | Yes | No | No |
| Skills (28 total) | Yes | Yes | Yes | Yes | N/A | N/A | No | No |
| Continue.dev configs | Yes | Yes | No | Partial | No | No | Yes (API keys) | No |
| Postman E2E collection | Yes | Yes | No | Yes | No | Manual only | No | No |
| MCP servers | No | N/A | N/A | No | No | No | N/A | Yes |
| Browser automation | No | N/A | No | No | No | No | N/A | Yes |
| Testcontainers | No | N/A | No | No | No | No | N/A | Yes |

---

## 10. Resource-Use Workflow

### Step 1: Identify task type

Classify the task into one of:
- **Frontend development** — React/TypeScript/Vite changes
- **Backend development** — Java/Spring Boot microservice changes
- **Infrastructure** — Docker, CI/CD, deployment configuration
- **Security** — OWASP, penetration testing, vulnerability remediation
- **Documentation** — Project docs, knowledge updates
- **Design** — UI/UX using stitch design system
- **Testing** — Unit tests, integration tests, E2E

### Step 2: Select relevant skills and agents

| Task Type | Recommended Skills | Recommended Agents |
|-----------|-------------------|-------------------|
| Frontend | typescript-pro, vercel-react-best-practices, frontend-design, web-design-guidelines, accessibility | explore, general |
| Backend | java-springboot, spring-boot-engineer, spring-boot-rest-api-standards, springboot-security, java-junit | explore, general |
| Security | owasp-security, owasp-top-10-testing, api-security-testing, find-security-vulnerabilities-in-code | explore, general |
| Database | postgresql-best-practices, postgresql-table-design | explore, general |
| Documentation | doc-coauthoring, internal-comms | general |
| Design | canvas-design, brand-guidelines, theme-factory | general |

### Step 3: Check tool availability

Verify before using:
- `mvn` for Java compilation/testing
- `npm` for frontend build/test/lint
- `docker` for container operations (unavailable locally)
- Git for version control

### Step 4: Read applicable instructions

- `AGENTS.md` — agent instructions
- `CLAUDE.md` — project rules
- `PROJECT_CONTEXT.md` — technology stack and conventions
- `PROJECT_STATUS.md` — current phase and blockers
- Skill SKILL.md files for task-specific guidance

### Step 5: Implement changes

- Follow AGENTS.md working principles
- Preserve all existing changes and untracked files
- Never use destructive Git operations
- Never expose secrets or credentials

### Step 6: Verify changes

| Check | Command |
|-------|---------|
| Compile one module | `mvn -pl services/<module> compile` |
| Test one module | `mvn -pl services/<module> test` |
| Full quality gate | `mvn -B verify -pl '!services/ai-service' -am` |
| Spotless | `mvn spotless:check` |
| Frontend typecheck | `cd frontend && npm run typecheck` |
| Frontend lint | `cd frontend && npm run lint` |
| Frontend tests | `cd frontend && npm run test:run` |
| Frontend build | `cd frontend && npm run build` |

### Step 7: Record evidence and update status

- Update `PROJECT_STATUS.md` after substantial tasks
- Record verification results
- Note any blockers or constraints

---

## 11. Persistent Findings

### What was discovered

- Complete SmartHire monorepo with 10 Maven modules (9 microservices + common library)
- React 18 + TypeScript 5 + Vite 5 frontend with 37 pages across public/candidate/recruiter/admin/shared
- 28 skills installed from GitHub sources (anthropics, usestrix/strix, wshobson/agents, etc.)
- Kilo Code agent manager with 1 tracked session
- GitHub Actions CI pipeline (test, spotless, spotbugs, security-scan)
- Docker Compose stack for local demonstration
- Postman E2E collection for manual testing
- Stitch design system with 33+ screen designs
- Session memory in `.remember/` directory
- Project knowledge in PROJECT_CONTEXT.md, PROJECT_STATUS.md, PROJECT_MEMORY.md

### What Kilo can genuinely use

- Maven build/test commands (verified working)
- Frontend build/test/lint commands (verified working)
- All 28 skills (discoverable via .claude/skills and .agents/skills)
- Kilo agent manager session management
- Project documentation (AGENTS.md, CLAUDE.md, PROJECT_CONTEXT.md, PROJECT_STATUS.md)
- Design system assets for UI reference
- Postman collection for manual API testing

### What requires configuration

| Item | Required Action | Blocker |
|------|-----------------|---------|
| deploy/.env | PostgreSQL credentials must be configured | User approval needed |
| Docker/Testcontainers | Not available locally | Cannot run integration tests |
| Browser automation | Not installed | Cannot run E2E frontend tests |
| MCP servers | Not configured | No MCP integration |
| Continue.dev | Placeholder configs only | API keys needed |
| Testcontainers | Not available | Cannot run DB-dependent tests |

### Recommended next actions

1. Address pre-existing TypeScript errors in UI components and page type definitions
2. Investigate registration/login HTTP 400 (DTO format)
3. Continue Phase 8 redesign work (frontend polish)
4. Fix remaining resilience4j gaps if any
5. Update PROJECT_STATUS.md after substantial tasks
