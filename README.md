# SmartHire — Online Job Recruitment System (Project 44)

Microservices platform for job recruitment, built for **24SDCS03A/R — SOA Programming and Microservices** (AY 2026-27).

> **Status: Complete up to Review-1 (Inc 0+1+2+3).** The system implements the full job-recruitment workflow: auth, job posting, applications, and recruitment management.

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

---

## Review-1 Demo Setup

This section describes how to set up, run, and demonstrate the complete SmartHire system (Inc 0+1+2+3) on a fresh laptop.

### Prerequisites

- **Docker + Docker Compose v2** (Desktop on Windows/macOS, or the Compose plugin on Linux)
- **Postman** (free, desktop or web app)
- **A web browser** (Chrome/Firefox/Edge)
- **JDK 21** and **Maven 3.9+** only if you want to rebuild or run tests

### What you get

| Service | Port | Purpose |
|---|---|---|
| `api-gateway` | 8080 | Single public entry point (all API traffic) |
| `service-registry` | 8761 | Eureka service discovery dashboard |
| `auth-service` | (internal) | JWT issuance + JWKS |
| `job-service` | (internal) | Job CRUD + `job_svc` schema |
| `application-service` | (internal) | Applications + `application_svc` schema |
| `recruitment-service` | (internal) | Recruitment workflow + `recruitment_svc` schema |
| `postgres` | (internal) | PostgreSQL 16 (all schemas) |
| `redis` | (internal) | JWT refresh-token store + denylist |
| `pgadmin` (optional) | 5050 | Database web UI for evidence |

### Step 1: Create your local environment file

```bash
cp deploy/.env.example deploy/.env
```

Then edit `deploy/.env` and fill in:

```ini
POSTGRES_DB=smarthire
POSTGRES_USER=smarthire
POSTGRES_PASSWORD=SetYourOwnStrongPasswordHere
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

The `deploy/.env.example` file lists all available keys. Only `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are **required**. The rest are optional — the auth-service will generate an ephemeral dev JWT key if you leave `AUTH_JWT_PRIVATE_KEY` blank, and the recruiter/admin seed accounts are configured out of the box (see Step 4 for login credentials).

### Step 2: Start the SmartHire stack

Open a terminal, navigate to the project root, and run:

```bash
# Option A: with pgAdmin (recommended for evidence gathering)
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.pgadmin.yml \
  --env-file deploy/.env up --build

# Option B: without pgAdmin (if you just want to run the demo flow)
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```

Wait for all containers to show `(healthy)`. This takes ~90 seconds on first run (images are built).

### Step 3: Verify all services are running

```bash
# All containers should show "healthy"
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.pgadmin.yml ps

# Quick smoke test through the gateway
curl -s http://localhost:8080/api/v1/jobs
```

Open <http://localhost:8761> in your browser — the Eureka dashboard should show all services registered:

- `API-GATEWAY`
- `SERVICE-REGISTRY`
- `AUTH-SERVICE`
- `JOB-SERVICE`
- `APPLICATION-SERVICE`
- `RECRUITMENT-SERVICE`

### Step 4: Demo credentials

| Role | Email | Password |
|---|---|---|
| Recruiter | `recruiter@smarthire.local` | `Recruiter123!` |
| Admin | `admin@smarthire.local` | `Admin123!` |
| Candidate | Register with any email (see Step 5 below) | Your chosen password |

These are seeded at startup from settings in `deploy/.env` (variables `AUTH_SEED_RECRUITER_EMAIL`/`AUTH_SEED_RECRUITER_PASSWORD` and `AUTH_SEED_ADMIN_EMAIL`/`AUTH_SEED_ADMIN_PASSWORD`). The default values above are the same as your existing `deploy/.env`.

### Step 5: Import the Postman collection

1. Open **Postman** (desktop or web).
2. Click **Import** → **Upload Files**.
3. Select `deploy/postman/SmartHire-Review1-E2E-Collection.json`.
4. Click **Import** again → select `deploy/postman/SmartHire-Review1-Environment.json`.
5. In the top-right environment dropdown, select **SmartHire Review-1**.
6. All 7 variables (`baseUrl`, `recruiterToken`, `candidateToken`, `jobId`, `applicationId`, `recruitmentId`, `candidateId`) are pre-configured. Click **Send** on each request in order.

### Step 6: Run the E2E demo flow (12 requests in the collection)

| # | Action | Who | What you'll see in Postman |
|---|---|---|---|
| 1 | **Recruiter Login** | Recruiter | Token auto-captured |
| 2 | **Candidate Registration** | Candidate | User created (status 200) |
| 3 | **Candidate Login** | Candidate | Token auto-captured |
| 4 | **Create Job** | Recruiter | Job ID auto-captured (status: DRAFT) |
| 5 | **Open Job** | Recruiter | Status: OPEN |
| 6 | **View Job** | Candidate | Sees the OPEN job |
| 7 | **Apply** | Candidate | Application ID auto-captured (status: APPLIED) |
| 8 | **Create Recruitment** | Recruiter | Recruitment ID auto-captured |
| 9 | **Schedule Interview** | Recruiter | Status: SCHEDULED |
| 10 | **Complete Interview** | Recruiter | Status: COMPLETED |
| 11 | **Set Final Status** | Recruiter | Final status: HIRED |
| 12 | **View Recruitment Status** | Candidate | Sees final status HIRED |

To run the whole collection at once: click the **Run** button (play icon) in Postman's collection runner, select the collection and environment, and click **Run Review-1 E2E Demo**.

### Step 7: View database evidence in pgAdmin

If you started with the pgAdmin override (Option A in Step 2):

1. Open <http://localhost:5050> in your browser.
2. **Login** with:
   - Email: `pgadmin@smarthire.com`
   - Password: `admin123!`
3. **Add a new server** (click the **+** icon or **File → Add Server**):
   - **General** tab → Name: `SmartHire-Postgres`
   - **Connection** tab:
     - Host name/address: `postgres`
     - Port: `5432`
     - Maintenance database: `smarthire`
     - Username: `smarthire`
     - Password: *(use the same password you set in `deploy/.env`)*
     - Check **Save password**
   - Click **Save**.
4. **Expand** the server in the left tree:
   - `SmartHire-Postgres` → **Databases** → `smarthire` → **Schemas**
5. You'll see four schemas:
   - `auth_svc` — users, organizations, refresh_tokens
   - `job_svc` — jobs
   - `application_svc` — applications
   - `recruitment_svc` — recruitments
6. **To view records**: Right-click any table (e.g. `jobs` under `job_svc`) → **View/Edit Data** → **All Rows**.

If you started without pgAdmin (Option B):
- Connect to the database via any PostgreSQL client using host `localhost`, port `5432` (you'll need to publish the port first — see the note below).
- Or start pgAdmin separately **after** the SmartHire stack is running:
  ```bash
  docker run -d --name pgadmin-smarthire --network smarthire_default -p 5050:80 \
    -e PGADMIN_DEFAULT_EMAIL=pgadmin@smarthire.com \
    -e PGADMIN_DEFAULT_PASSWORD=admin123! \
    dpage/pgadmin4
  ```
  > The `--network smarthire_default` flag connects pgAdmin to the same Docker network as your SmartHire services.

> **Note**: By default, PostgreSQL is **not exposed** to the host (no port mapping in `docker-compose.yml` for security). pgAdmin connects from within the `smarthire_default` Docker network using hostname `postgres`. If you need direct host access, add a `ports` entry to the postgres service in a local override file.

### Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused` on `localhost:8080` | Wait 90s+ for all containers to become healthy |
| 401 on API calls | Ensure you ran the **Login** request first to get a valid token |
| Eureka dashboard shows no services | Wait for health checks to pass; check `docker compose -f deploy/docker-compose.yml ps` |
| pgAdmin can't connect to postgres | Use hostname `postgres` (not `localhost`), port `5432`, and verify the password matches your `deploy/.env` |
| Candidate registration returns 409 | The candidate already exists from a previous run — just run **Login** instead |
| `docker compose: command not found` | Install Docker Compose v2 (comes with Docker Desktop) or use `docker-compose` (v1) instead |

### Stopping the demo

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.pgadmin.yml down
```

This stops all containers but preserves the PostgreSQL database volume. To start fresh (delete all demo data), add `-v`:

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.pgadmin.yml down -v
```
