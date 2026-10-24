# SmartHire — Agent Instructions

Persistent instructions for Claude/Kilo Code coding agents working in this repository. Stored at the repository root as `AGENTS.md`. Kilo Code uses root-level `AGENTS.md` as its project-instructions mechanism; `CLAUDE.md` (also root) contains the project rules and development workflow.

## Read first

1. Read `PROJECT_CONTEXT.md` and `PROJECT_STATUS.md` before making changes.
2. Inspect `git status` and relevant diffs before editing.
3. Do not overwrite `phase2_report.md` — it is owned by Claude for ongoing Phase 2 verification.

## Working principles

- Verify only task-relevant files; avoid repeating a full audit without a concrete reason.
- Search for existing patterns before implementing new ones; reuse known conventions.
- Reuse verified build/test commands, but rerun checks affected by your changes.
- Run broader checks (Spotless, tests, `mvn verify` where possible) at phase boundaries and when cross-service behavior is affected.
- Avoid unnecessary subagents and duplicate investigations.
- Preserve all user changes and untracked files.
- Never expose secrets or credentials; never read out `.env` file contents.
- Never use destructive Git operations (no reset, clean, stash, revert, commit, push, branch switches, history rewrites).
- Never claim tests passed unless they were actually run.
- Update `PROJECT_STATUS.md` after substantial tasks.
- Keep context concise; do not duplicate the same information in multiple files.

## Verified build/test commands

- Compile one module: `mvn -pl services/<module> compile`
- Test one module: `mvn -pl services/<module> test` (e.g. `services/application-service` → 125 tests passing, reported)
- Full quality gate: `mvn verify` (CI excludes `services/ai-service`: `mvn -B verify -pl '!services/ai-service' -am`)
- Spotless: `mvn spotless:check` (passes on 203 files, reported)
- Frontend (from `frontend/`): `npm run lint`, `npm run typecheck`, `npm run test:run`, `npm run build` — all must exit 0 before publishing frontend changes

## Constraints

- Docker/Testcontainers unavailable locally → integration tests and live runtime verification cannot run locally.
- Host JDK 24 vs Java 21 build target → Surefire/Failsafe need `-Dnet.bytebuddy.experimental=true`; SpotBugs skipped locally (class file version 68).
- `services/ai-service` excluded from CI verify (compilation/test infra issues); its tests need PostgreSQL.

## Kilo Code configuration

- Kilo config: `.kilo/kilo.jsonc` (minimal, schema reference only)
- Project rules: `CLAUDE.md` (root)
- Agent instructions: this file, `AGENTS.md` (root)
- Context: `PROJECT_CONTEXT.md` + `PROJECT_STATUS.md` (root, this session)
