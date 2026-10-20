<!--
SmartHire README — enhanced with 3D cards, CSS animations, SVG illustrations, and top-repo design inspiration.
GitHub strips <script> and <style> in READMEs, so all motion is achieved with inline SVG/CSS and animated assets.
-->
<div align="center">

# SmartHire

**Online Job Recruitment Platform — Microservices & SOA**

A production-grade, full-stack job recruitment system built with **Spring Boot 3.x**, **Spring Cloud**, **PostgreSQL**, **Redis**, **React + TypeScript + Vite**, and **Docker Compose**. Auth, job posting, applications, recruitment workflows, documents, and notifications — all behind a single API Gateway with Eureka service discovery.

</div>

---

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=java&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-5-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)

</div>

---

## 🚀 One-line demo

```bash
# 1. Clone
git clone https://github.com/Hareesh-Reddy-9126/PS044-SmartHire.git
cd PS044-SmartHire

# 2. Configure environment
cp deploy/.env.example deploy/.env
# edit deploy/.env -> set POSTGRES_USER / POSTGRES_PASSWORD

# 3. Start the whole stack
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build

# 4. Smoke test through the gateway
curl http://localhost:8080/api/v1/jobs
```

---

## ✨ Features

<div align="center">

<table>
  <tr>
    <td>
      <div class="card3d">
        <div class="face front">🔐<br/><b>Auth & JWT</b></div>
        <div class="face back">Register, login, refresh, JWKS, role-based access (candidate/recruiter/admin).</div>
      </div>
    </td>
    <td>
      <div class="card3d">
        <div class="face front">💼<br/><b>Jobs</b></div>
        <div class="face back">Create, publish, browse, filter, paginate, search job postings.</div>
      </div>
    </td>
    <td>
      <div class="card3d">
        <div class="face front">📄<br/><b>Applications</b></div>
        <div class="face back">Apply to jobs, track status, recruiter review pipeline.</div>
      </div>
    </td>
  </tr>
  <tr>
    <td>
      <div class="card3d">
        <div class="face front">🤝<br/><b>Recruitment</b></div>
        <div class="face back">Schedule interviews, complete interviews, set final hire/reject status.</div>
      </div>
    </td>
    <td>
      <div class="card3d">
        <div class="face front">📎<br/><b>Documents</b></div>
        <div class="face back">Resume upload, document storage, recruiter/candidate access rules.</div>
      </div>
    </td>
    <td>
      <div class="card3d">
        <div class="face front">🔔<br/><b>Notifications</b></div>
        <div class="face back">Status updates, email/websocket alerts to candidates and recruiters.</div>
      </div>
    </td>
  </tr>
</table>

</div>

<style>
.card3d {
  perspective: 1000px;
  display: inline-block;
  width: 210px;
  height: 140px;
  position: relative;
  transition: transform 0.25s;
}
.card3d:hover { transform: rotateY(10deg) rotateX(5deg) scale(1.03); }
.card3d .face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  font-family: system-ui, -apple-system, sans-serif;
  font-size: 12px;
  line-height: 1.3;
  color: #fff;
  box-shadow: 0 8px 24px rgba(0,0,0,0.18);
  border: 1px solid rgba(255,255,255,0.25);
}
.card3d .front { background: linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%); }
.card3d .back  { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); transform: rotateY(180deg); }
.card3d .face b { display: block; margin-top: 4px; font-size: 14px; }
</style>

---

## 🧱 Architecture

<div align="center">

```text
  Browser / Postman
        │
        ▼
┌─────────────────────┐     ┌──────────────────┐
│   API Gateway       │────▶│  Eureka Server   │
│  (port 8080)        │     │  (port 8761)     │
└──────────┬──────────┘     └──────────────────┘
           │
      ┌────┴────┐
      ▼         ▼
┌─────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐
│ Auth    │ │ Job      │ │ Application  │ │ Recruitment  │ │ Other svc  │
│ Service │ │ Service  │ │ Service      │ │ Service      │ │ …          │
└────┬────┘ └────┬─────┘ └──────┬───────┘ └──────┬───────┘ └─────┬──────┘
     │           │              │                │               │
     └───────────┴──────────────┴────────────────┴─────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
     PostgreSQL 16           Redis 7
  (per-service schemas)   (refresh tokens, denylist)
```

</div>

> **Service mesh:** API Gateway → Eureka discovery → Feign inter-service calls → per-service PostgreSQL schemas. JWT issued by Auth Service, validated at the gateway and per service.

---

## 🛠 Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Spring Cloud, Spring Security 6, Spring Data JPA, OpenFeign |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, React Router |
| API docs | OpenAPI / Swagger (springdoc) |
| Messaging/Store | Redis (refresh tokens, rate limiting, denylist) |
| Database | PostgreSQL 16 (one schema per service) |
| Container | Docker, Docker Compose v2 |
| Build | Maven, Spotless, SpotBugs, ArchUnit, Testcontainers |
| Testing | JUnit 5, WebMvc, Vitest + React Testing Library |

---

## 📦 Modules

| Module | Port | Responsibility |
|---|---|---|
| `common` | — | Correlation-id filter + RFC 9457 Problem Details advice |
| `services/service-registry` | 8761 | Eureka service discovery |
| `services/api-gateway` | 8080 | Spring Cloud Gateway, routing, security, rate limiting |
| `services/auth-service` | internal | JWT issuance, JWKS, registration, roles |
| `services/job-service` | internal | Job CRUD, `job_svc` schema |
| `services/application-service` | internal | Applications, `application_svc` schema |
| `services/recruitment-service` | internal | Recruitment workflow, `recruitment_svc` schema |
| `services/document-service` | internal | Document management |
| `services/notification-service` | internal | Notifications |
| `frontend` | 5173 | React SPA (Vite dev server) |

---

## ▶️ Run the demo stack

### Prerequisites
- Docker + Docker Compose v2
- (Optional) JDK 21 + Maven 3.9+ to rebuild or run tests

### Steps

1. **Create your environment file** (git-ignored — never commit secrets):
   ```bash
   cp deploy/.env.example deploy/.env
   ```
   Edit `deploy/.env` and set `POSTGRES_USER` and `POSTGRES_PASSWORD`.

2. **Start everything:**
   ```bash
   # With pgAdmin (recommended for DB evidence)
   docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.pgadmin.yml \
     --env-file deploy/.env up --build

   # Without pgAdmin
   docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
   ```

3. **Verify through the gateway:**
   ```bash
   curl -s http://localhost:8080/api/v1/jobs
   ```

4. **Eureka dashboard:** <http://localhost:8761>

Wait ~90 s for all containers to become healthy on first build.

---

## 🔑 Demo credentials

| Role | Email | Password |
|---|---|---|
| Recruiter | `recruiter@smarthire.local` | `Recruiter123!` |
| Admin | `admin@smarthire.local` | `Admin123!` |
| Candidate | Register any email | Your chosen password |

---

## 🧪 Quality gate

```bash
# Full build + unit + integration tests + static analysis
mvn verify

# Frontend
cd frontend && npm ci && npm run typecheck && npm run lint && npm run test:run && npm run build
```

Backend integration tests use **Testcontainers** with real PostgreSQL/Redis — no H2 fallback.

---

## 🗺 Roadmap

- [x] Inc 0 — Core job browse through gateway + Eureka
- [x] Inc 1 — Auth & JWT (login, register, refresh, JWKS)
- [x] Inc 2 — Applications + recruitment workflow
- [x] Inc 3 — Documents + notifications
- [x] Inc 4 — Quality, security, CI hardening
- [x] Frontend React SPA (login, register, job browse)
- [ ] Inc 5+ — AI matching, evaluation harness, Kubernetes deployment

See `docs/roadmap` and `docs/design.md` for details.

---

## 🤝 Contributing

1. Fork the repo
2. Create your feature branch (`git checkout -b feat/your-feature`)
3. Commit your changes (`git commit -m "feat: ..."`)
4. Push to the branch (`git push origin feat/your-feature`)
5. Open a Pull Request

Please follow the existing commit style (`feat:`, `fix:`, `docs:`, `chore:`) and run `mvn verify` before opening a PR.

---

## 📄 License

MIT License. See `LICENSE` for details.

---

<div align="center">

Built with ☕ + ⚛️ for **24SDCS03A/R — SOA Programming and Microservices** (AY 2026-27).

[⭐ Star this repo](https://github.com/Hareesh-Reddy-9126/PS044-SmartHire/stargazers) · [🍴 Fork](https://github.com/Hareesh-Reddy-9126/PS044-SmartHire/fork) · [🐛 Report Bug](https://github.com/Hareesh-Reddy-9126/PS044-SmartHire/issues)

</div>
