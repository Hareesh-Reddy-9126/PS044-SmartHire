# Repository Assessment — PS044-SmartHire

_Phase 0 reconnaissance. Read-only inspection performed 2026-09-14. No application files modified._

## Summary

This is a **greenfield repository**. It contains project governance (CLAUDE.md),
a curated Claude Code toolchain (skills + plugins), and Git scaffolding — but
**zero application source code**, no build manifests, and **no product/requirements
documentation**. The technology *intent* is legible from the installed tooling; the
*product scope* is not present in the repository and must be supplied.

## What exists (verified)

| Area | Finding |
|---|---|
| Application source | **None.** No `src/`, no code. |
| Build manifests | **None.** No `pom.xml`, `build.gradle`, `package.json`, `tsconfig.json`. |
| Containerization / infra | **None.** No `Dockerfile`, `docker-compose`, IaC. |
| Database artifacts | **None.** No `*.sql`, migrations, or schema. |
| App configuration | **None.** No `application.yml/.properties`, no `.env.example`. |
| CI/CD | **None.** No `.github/`, no pipelines. |
| Product / requirements docs | **None.** No README describing the product, no problem statement. |
| Git | Branch `main`, clean tree, 3 initialization ("chore") commits only. |
| `.gitignore` | Declares ignores for **Node** (`node_modules`, npm/yarn/pnpm) and **Java/Spring Boot** (`target/`, `*.class`), plus `.env*` secrets, IDE, logs, OS files. |
| `CLAUDE.md` | Project rules: new project, simple/maintainable, no unjustified deps, never expose secrets, test before done, incremental changes, security-first, inspect before changing, justify architecture. |
| `.claude/settings.local.json` | Enabled plugins: `ponytail`, `superpowers`, `frontend-design`, `code-review`, `sentry`, `redis-development`. |
| Skills (`skills-lock.json`) | 49 skills pinned (see below). |

## Technology intent signaled by the toolchain

The installed skills + plugins form a coherent stack signature. This indicates
*intended technology*, not a confirmed architectural decision:

- **Backend:** `java-springboot`, `spring-boot-engineer`, `spring-boot-rest-api-standards`, `springboot-security`, `java-junit` → Spring Boot (Java) REST API + JUnit.
- **Database:** `postgresql-best-practices`, `postgresql-table-design` → PostgreSQL.
- **Cache / infra:** `redis-development` plugin → Redis.
- **Frontend:** `typescript-pro`, `vercel-react-best-practices`, `frontend-design`, `web-design-guidelines`, `accessibility`, `theme-factory`, `brand-guidelines` → React + TypeScript.
- **AI / LLM:** `claude-api`, `llm-evaluation`, `llm-security`, `prompt-master`, `discernment-nudge` → Claude API with evaluation + LLM security.
- **Documents:** `pdf`, `docx`, `xlsx`, `pptx` → document ingestion/generation (e.g. résumés, reports).
- **Security (heavy):** `owasp-security`, `owasp-top-10-testing`, `api-security-testing`, `application-security-testing`, `find-security-vulnerabilities-in-code`, `fix-security-vulnerabilities-with-strix`, `web-app-penetration-testing`, `penetration-testing-with-strix`, `managed-pentesting-with-strix`, `ci-security-scanning-with-strix`.
- **Testing:** `e2e-testing-patterns`, `webapp-testing`, `java-junit`.
- **Observability:** `sentry` plugin.
- **Meta/tooling:** `mcp-builder`, `harness-creator`, `skill-creator`, `find-skills`, `gstack`, `doc-coauthoring`.

## Conclusion

Nothing to reuse or avoid reviving — the slate is genuinely clean. The project is
ready for **requirements definition**, which is the true blocker: the toolchain tells
us *how* we are likely expected to build, but the repository does not state *what*
SmartHire must do. See `requirements-register.md`.
