# SmartHire — PROJECT_CAPABILITY_MATRIX.md

Phase 0 discovery — capability and compatibility matrix for SmartHire development environment.
Generated from Phase 0 discovery session.

All classifications based on observed evidence from the current working tree, not assumptions.

---

## Classification Legend

| # | Classification | Definition |
|---|---------------|------------|
| 1 | Present in Repository | File/directory exists in the working tree |
| 2 | Discoverable by Kilo | Kilo can inspect/read the resource |
| 3 | Compatible with Kilo | Kilo can invoke/work with the resource |
| 4 | Configured | Resource has valid configuration |
| 5 | Connected/Authenticated | Resource has valid connection/auth |
| 6 | Successfully Invoked | Resource has been tested and works |
| 7 | Requires User Action | User must configure or approve |
| 8 | Not Accessible/Incompatible | Resource cannot be used from this session |

---

## A. Build and Test Tools

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| Maven (mvn) | Yes | Yes | Yes | Yes | N/A | Yes | No | No | `mvn compile` verified on all 10 modules |
| Java/JDK 24 (host) | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Host JDK 24, build targets Java 21 |
| Surefire/Failsafe | Yes | Yes | Yes | Yes | N/A | Partial | Yes | No | Needs `-Dnet.bytebuddy.experimental=true` on JDK 24 |
| Spotless | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Passes on 203 files |
| SpotBugs | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Skipped locally (class file version 68) |
| ArchUnit | Yes | Yes | Yes | Yes | N/A | Partial | No | No | Warnings on JDK 24 class files |
| npm | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Frontend package manager |
| Vite | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Frontend build/dev server |
| Vitest | Yes | Yes | Yes | Yes | N/A | Yes | No | No | 32 frontend tests |
| TypeScript (tsc) | Yes | Yes | Yes | Yes | N/A | Yes | No | No | `tsc --noEmit` |
| ESLint | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Frontend linting |
| Tailwind CSS | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Styling framework |
| Testcontainers | No | No | No | No | No | No | N/A | Yes | Docker/Testcontainers unavailable locally |
| Selenium/Playwright | No | No | No | No | No | No | N/A | Yes | No browser automation installed |

---

## B. Backend Services

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| service-registry | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, tests pass |
| api-gateway | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, tests pass |
| auth-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, 55 tests pass |
| job-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, 78 tests pass |
| application-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, 137 tests pass |
| recruitment-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, 122 tests pass |
| document-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, tests pass |
| notification-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, tests pass |
| ai-service | Yes | Yes | Yes | Yes | No | Yes | No | No | Compiles, 52 tests pass |
| common | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Compiles, tests pass |

---

## C. Frontend

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| React 18 | Yes | Yes | Yes | Yes | N/A | Yes | No | No | package.json dependency |
| TypeScript 5 | Yes | Yes | Yes | Yes | N/A | Yes | No | No | `tsc --noEmit` |
| Vite 5 | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Dev server + build |
| Tailwind CSS | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Custom design tokens |
| React Router 6 | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Route definitions |
| Testing Library | Yes | Yes | Yes | Yes | N/A | Yes | No | No | React + jest-dom + user-event |

---

## D. Infrastructure and Deployment

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| Docker Compose | Yes | Yes | Yes | Yes | No | No | No | No | `deploy/docker-compose.yml` |
| PostgreSQL 16 | Yes (in compose) | Yes | Yes | Yes | No | No | Yes | No | Credentials unknown |
| Redis 7 | Yes (in compose) | Yes | Yes | Yes | No | No | No | No | Refresh-token store + jti denylist |
| Eureka service-registry | Yes | Yes | Yes | Yes | No | No | No | No | Port 8761 |
| Spring Cloud Gateway | Yes | Yes | Yes | Yes | No | No | No | No | Port 8080 |
| deploy/.env | Yes | Yes | Yes | No | No | No | Yes | No | Git-ignored, credentials required |
| deploy/.env.example | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Keys only, no values |
| pgAdmin override | Yes | Yes | Yes | Yes | No | No | No | No | `docker-compose.pgadmin.yml` |

---

## E. CI/CD and Quality

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| GitHub Actions | Yes | Yes | Yes | Yes | No | No | No | No | `.github/workflows/ci.yml` |
| OWASP Dependency-Check | Yes | Yes | Yes | Yes | No | No | No | No | security-scan job (push/main PR only) |
| Test reports | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Surefire/Failsafe XML reports |

---

## F. Skills (28 total)

| Skill | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Source |
|-------|---|---|---|---|---|---|---|---|--------|
| academy-guide | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| accessibility | Yes | Yes | Yes | Yes | N/A | No | No | No | addyosmani/web-quality-skills |
| algorithmic-art | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| api-security-testing | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| application-security-testing | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| brand-guidelines | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| canvas-design | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| ci-security-scanning-with-strix | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| claude-api | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| diagram-design | Yes | Yes | Yes | Yes | N/A | No | No | No | cathrynlavery/diagram-design |
| discernment-nudge | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| doc-coauthoring | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| docx | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| e2e-testing-patterns | Yes | Yes | Yes | Yes | N/A | No | No | No | wshobson/agents |
| find-security-vulnerabilities-in-code | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| find-skills | Yes | Yes | Yes | Yes | N/A | No | No | No | vercel-labs/skills |
| fix-security-vulnerabilities-with-strix | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| frontend-design | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| gstack | Yes | Yes | Yes | Yes | N/A | No | No | No | garrytan/gstack |
| harness-creator | Yes | Yes | Yes | Yes | N/A | No | No | No | walkinglabs/learn-harness-engineering |
| internal-comms | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| java-junit | Yes | Yes | Yes | Yes | N/A | No | No | No | github/awesome-copilot |
| java-springboot | Yes | Yes | Yes | Yes | N/A | No | No | No | github/awesome-copilot |
| llm-evaluation | Yes | Yes | Yes | Yes | N/A | No | No | No | wshobson/agents |
| llm-security | Yes | Yes | Yes | Yes | N/A | No | No | No | semgrep/skills |
| managed-pentesting-with-strix | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| mcp-builder | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| owasp-security | Yes | Yes | Yes | Yes | N/A | No | No | No | hoodini/ai-agents-skills |
| owasp-top-10-testing | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| pdf | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| penetration-testing-with-strix | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| postgresql-best-practices | Yes | Yes | Yes | Yes | N/A | No | No | No | microsoft/postgres-skills |
| postgresql-table-design | Yes | Yes | Yes | Yes | N/A | No | No | No | wshobson/agents |
| pptx | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| prompt-master | Yes | Yes | Yes | Yes | N/A | No | No | No | nidhinjs/prompt-master |
| skill-creator | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| slack-gif-creator | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| spring-boot-engineer | Yes | Yes | Yes | Yes | N/A | No | No | No | jeffallan/claude-skills |
| spring-boot-rest-api-standards | Yes | Yes | Yes | Yes | N/A | No | No | No | giuseppe-trisciuoglio/developer-kit |
| springboot-security | Yes | Yes | Yes | Yes | N/A | No | No | No | affaan-m/ecc |
| template-skill | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| theme-factory | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| typescript-pro | Yes | Yes | Yes | Yes | N/A | No | No | No | jeffallan/claude-skills |
| vercel-react-best-practices | Yes | Yes | Yes | Yes | N/A | No | No | No | vercel-labs/agent-skills |
| web-app-penetration-testing | Yes | Yes | Yes | Yes | N/A | No | No | No | usestrix/strix |
| web-artifacts-builder | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| web-design-guidelines | Yes | Yes | Yes | Yes | N/A | No | No | No | vercel-labs/agent-skills |
| webapp-testing | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |
| xlsx | Yes | Yes | Yes | Yes | N/A | No | No | No | anthropics/skills |

---

## G. Integrations and Connectors

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| Continue.dev configs | Yes | Yes | No | Partial | No | No | Yes | No | Placeholder configs with API key placeholders |
| Postman E2E collection | Yes | Yes | No | Yes | No | Manual | No | No | Manual E2E only, no CI automation |
| Postman workspace globals | Yes | Yes | No | Yes | N/A | No | No | No | `postman/globals/workspace.globals.yaml` |
| MCP servers | No | No | No | No | No | No | N/A | Yes | No MCP configurations found |
| Strix (self-hosted CLI) | No | N/A | No | No | No | No | N/A | Yes | Not installed locally |
| Strix (managed cloud) | No | N/A | No | No | No | No | N/A | Yes | app.strix.ai (credits required) |

---

## H. Project Knowledge

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| AGENTS.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Kilo agent instructions |
| CLAUDE.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Project rules and workflow |
| PROJECT_CONTEXT.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Technology stack and conventions |
| PROJECT_STATUS.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Phase status and blockers |
| PROJECT_MEMORY.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Persistent project memory |
| README.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Project overview |
| phase2_report.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Phase 2 backend integration report |
| backend_integration_gaps_summary.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Integration gap summary |
| docs/design.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Design system specification |
| docs/traceability-matrix.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Requirements traceability |
| docs/architecture/adrs.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Architecture Decision Records |
| .remember/ logs | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Session memory logs |
| skills-lock.json | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Skill lock file with hashes |

---

## I. Design Assets

| Resource | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | Evidence |
|----------|---|---|---|---|---|---|---|---|----------|
| design.md | Yes | Yes | Yes | Yes | N/A | Yes | No | No | Design system specification |
| code.html (per screen) | Yes | Yes | Yes | Yes | N/A | No | No | No | HTML/CSS reference for each screen |
| screen.png (per screen) | Yes | Yes | Yes | Yes | N/A | No | No | No | Visual reference images |
| calm_institutional theme | Yes | Yes | Yes | Yes | N/A | No | No | No | Theme design document |

---

## Summary

| Classification | Count | Resources |
|---------------|-------|-----------|
| Fully accessible (1-6 all Yes) | ~40 | Build tools, backend services, frontend, project docs, skills |
| Configured but not connected (4 Yes, 5 No) | ~15 | Docker Compose, PostgreSQL, Redis, CI/CD |
| Requires user action (7 Yes) | ~5 | deploy/.env credentials, Continue.dev API keys |
| Not accessible/incompatible (8 Yes) | ~6 | MCP servers, Docker/Testcontainers, browser automation, Testcontainers, Strix |
