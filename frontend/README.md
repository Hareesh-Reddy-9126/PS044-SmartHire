# SmartHire Frontend

React + TypeScript + Vite frontend for the SmartHire job recruitment platform.

## Quick Start

### Prerequisites
- Node.js 18+
- npm 9+
- Backend running on `http://localhost:8080` (via `docker compose -f deploy/docker-compose.yml up`)

### Installation
```bash
cd frontend
npm install
```

### Development
```bash
npm run dev
```
Runs on `http://localhost:5173` with proxy to `http://localhost:8080` for `/api`, `/oauth2`, `/actuator`, `/v3`.

### Build
```bash
npm run build
```
Outputs to `dist/`.

### Type Check
```bash
npm run typecheck
```

### Lint
```bash
npm run lint
```

### Test
```bash
npm run test:run
```

## Architecture

```
src/
├── api/           # API client (typed fetch wrappers)
├── components/
│   ├── ui/        # Reusable UI primitives (Button, Input, Modal, etc.)
│   ├── layout/    # Layout components (TopBar, Sidebar, Layout shells)
│   ├── forms/     # Form-specific components
│   ├── data-display/
│   └── feedback/
├── context/       # React Context providers (Auth, Toast)
├── hooks/         # Custom hooks (useApi, useLocalStorage)
├── pages/
│   ├── auth/      # Login, Register
│   ├── public/    # Public job browse
│   ├── candidate/ # Candidate screens
│   ├── recruiter/ # Recruiter screens
│   └── admin/     # Admin screens
├── routes/        # React Router configuration
├── styles/        # Tailwind + design tokens
└── utils/         # Utilities
```

## Authentication

- Access tokens stored **in memory only** (never localStorage/sessionStorage)
- Refresh token is an `HttpOnly; Secure` cookie managed by the browser
- 15-minute access token lifetime with proactive refresh scheduling
- On `http://localhost` the `Secure` cookie is rejected — silent refresh fails; this is expected behavior

## API Contract

All API calls go through the Gateway at `http://localhost:8080`:
- `/api/v1/auth/*` — Authentication
- `/api/v1/jobs/*` — Job postings
- `/api/v1/applications/*` — Applications
- `/api/v1/recruitments/*` — Recruitment workflows
- `/api/v1/documents/*` — Document management
- `/api/v1/notifications/*` — Notifications
- `/oauth2/jwks` — JWKS endpoint
- `/actuator/*` — Health/metrics

## Design System

Tokens extracted from `stitch_smarthire_recruitment_design_system/calm_institutional/DESIGN.md`:
- Colors: Primary (Ink Indigo), Secondary (Teal), Semantic status colors
- Typography: Inter (UI), JetBrains Mono (monospace)
- Spacing: 4px baseline rhythm
- Components: Buttons, Inputs, StatusBadge, RoleChip, Modal, Card, Tables

## Implemented Screens (Phase 1)

- ✅ `/login` — Sign in with RFC 9457 error handling
- ✅ `/register` — Candidate registration with password strength meter
- ✅ `/jobs` — Public job browse with filtering, pagination, debounced search

## Remaining Screens (Future Phases)

See `docs/design.md` for the complete 36-screen specification.

## Docker

Not configured in Phase 1. Will be added in Phase 4.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | `` (uses Vite proxy) | API Gateway base URL |
| `VITE_DEBUG` | `false` | Enable debug logging |

## Backend Integration Notes

- **GAP-01**: No `GET /applications` list endpoint — candidate applications composed from `/recruitments` + local registry
- **GAP-02**: Recruiters cannot read documents — 404 always
- **GAP-08**: Refresh cookie requires `Secure` — fails on `localhost` HTTP
- **GAP-09/10**: No password reset or profile editing endpoints