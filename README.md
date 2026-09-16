# SmartHire — Online Job Recruitment System (Project 44)

Microservices platform for job recruitment, built for **24SDCS03A/R — SOA Programming and Microservices** (AY 2026-27).

> **Status: Increment 0 — project skeleton.** This increment stands up the microservices backbone and one thin vertical slice through it. It is intentionally minimal; see [Scope](#increment-0-scope) below.

## Increment 0 scope

Inc 0 delivers the smallest system that demonstrates the core service-oriented path end to end:

```
client ──▶ API Gateway ──▶ Eureka discovery ──▶ job-service ──▶ GET /api/v1/jobs
 (curl)      :8080          (lb://JOB-SERVICE)     :8081           (Postgres: job_svc.jobs)
```

The gateway does not know where job-service lives; it resolves `lb://JOB-SERVICE` through Eureka at request time. That indirection is the point of the increment.

### Modules

| Module | Artifact | Port | Responsibility |
|---|---|---|---|
| `common` | `smarthire-common` | — | Shared servlet correlation-id filter + RFC 9457 Problem Details advice (Spring Boot auto-configuration). |
| `services/service-registry` | `smarthire-service-registry` | 8761 | Eureka server (service discovery). |
| `services/api-gateway` | `smarthire-api-gateway` | 8080 | Spring Cloud Gateway — the single public entry point. Routes `/api/v1/jobs/**` to `lb://JOB-SERVICE`. |
| `services/job-service` | `smarthire-job-service` | 8081 | Owns the `Job` aggregate and the `job_svc` schema. Exposes `GET /api/v1/jobs`. |

### Explicitly NOT in Inc 0

Authentication/JWT, other domain services (application, recruitment, document, notification), the React SPA, Redis, Kafka, config server, OpenFeign inter-service calls, MinIO/object storage, AI features, Kubernetes, and cloud deployment. These belong to later increments per the roadmap.

## Prerequisites

- **JDK 21** (the build targets Java 21 bytecode). See [known deviations](#known-inc-0-decisions--deviations) about the host JDK.
- **Maven 3.9+** (or use the bundled wrapper conventions of your setup).
- **Docker + Docker Compose v2** — required both to run the demo stack and to run the integration tests (Testcontainers starts a real PostgreSQL; there is no H2 fallback).

## Build and test

The full local quality gate compiles every module, runs unit tests, runs Testcontainers integration tests against real PostgreSQL, and enforces formatting (Spotless), static analysis (SpotBugs), and architecture rules (ArchUnit):

```bash
mvn verify
```

Optional dependency vulnerability scan (OWASP Dependency-Check; network-bound, slower):

```bash
mvn verify -Psecurity-scan
```

## Run the demonstration stack

1. Create your local environment file (git-ignored — never committed):

   ```bash
   cp deploy/.env.example deploy/.env
   ```

   Then edit `deploy/.env` and set `POSTGRES_USER` and `POSTGRES_PASSWORD` (and optionally `POSTGRES_DB`, `GATEWAY_CORS_ALLOWED_ORIGINS`).

2. Build and start everything:

   ```bash
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
   ```

   Compose waits for health: Postgres → service-registry → job-service → api-gateway.

3. Verify the demonstrable flow **through the gateway** (not by hitting job-service directly):

   ```bash
   curl http://localhost:8080/api/v1/jobs
   ```

   Expect a JSON array containing the seeded jobs (`Acme Corp`, `Globex Inc`). Every response carries an `X-Correlation-Id` header.

4. Confirm discovery: open the Eureka dashboard at <http://localhost:8761> and check that `JOB-SERVICE` and `API-GATEWAY` are registered.

## Configuration and secrets

- No secrets live in source or in committed config. Runtime secrets come from the environment.
- `deploy/.env` is git-ignored; `deploy/.env.example` lists keys only, never values.
- Every service exposes `/actuator/health` for Compose health checks.

## Known Inc-0 decisions & deviations

These are deliberate, documented choices for the skeleton increment:

- **`job_id` is a `UUID`.** The architecture does not mandate a primary-key type; UUID is chosen for cross-service opacity and to avoid shared sequence coupling.
- **Shared DB user.** job-service connects as the single shared Postgres app user. Per-schema least-privilege DB users are deferred to Inc 3, when multiple schemas coexist.
- **Host JDK vs build target.** The build targets Java 21. Docker images build and run on Temurin 21 regardless of host. Running `mvn verify` directly on a newer host JDK is a local-only convenience, not the shipped runtime.
- **Image digest pinning** (governance §34) is deferred to CI, where digests can be resolved and recorded honestly rather than hand-fabricated.
