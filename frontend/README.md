# SmartHire Frontend

React + TypeScript + Vite client for the SmartHire recruitment platform. Talks to the API Gateway and covers public job discovery plus candidate, recruiter, and admin workspaces.

## Stack

- React 18, TypeScript 5, Vite 5
- Tailwind CSS design tokens (`tailwind.config.js`, `src/styles/index.css`)
- React Router 6
- Vitest + Testing Library (49 tests across 8 files)
- No UI framework beyond Tailwind and Material Symbols icons

## Prerequisites

- Node.js 18+
- npm 9+
- API Gateway reachable at `http://localhost:8080` (for example via `docker compose -f deploy/docker-compose.yml up`)

## Setup

```bash
cd frontend
npm install
```

### Environment

Copy `.env.example`. Supported variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | empty (Vite proxy) | Absolute Gateway base URL for non-proxy deployments |
| `VITE_DEBUG` | `false` | Extra client logging when enabled |

Do not commit files that contain real secrets.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Vite dev server on `http://localhost:5173` |
| `npm run build` | Typecheck + production bundle to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint with zero warnings allowed |
| `npm run test` | Vitest watch mode |
| `npm run test:run` | Single Vitest run (CI) |

### Dev proxy

Vite proxies `/api`, `/oauth2`, `/actuator`, and `/v3` to `http://localhost:8080`. Production builds must set `VITE_API_BASE_URL` or be served behind a reverse proxy that forwards those paths; the Vite proxy is not used in production.

## Architecture

```
src/
├── api/client.ts          # Typed Gateway client, in-memory access token
├── components/
│   ├── layout/Layout.tsx  # App shell, public layout, and auth layout
│   └── ui/                # Full design system component library (see below)
├── context/               # AuthContext (token management), ToastContext
├── hooks/useApi.ts        # Generic data-fetching hook with loading/error state
├── pages/
│   ├── auth/              # LoginPage, RegisterPage
│   ├── public/            # JobBrowsePage, JobDetailPage
│   ├── candidate/         # Applications, progress, documents, notifications, profile
│   ├── recruiter/         # Jobs CRUD, recruitments, application lookup, profile
│   ├── admin/             # Overview, recruitments list, application lookup, profile
│   └── shared/            # ProfilePage, error pages (403, 404, 500, session-expired, offline)
├── routes/AppRoutes.tsx   # Role-gated routing with ProtectedRoute and WorkspaceRedirect
├── styles/index.css       # Design system utility classes and CSS custom properties
└── utils/                 # errors.ts (ApiErrorResponse), format.ts (dates, text), roles.ts (role routing)
```

## Design System — `src/components/ui/`

"The decision record" system: deep evergreen ink, chalk paper, restrained brass; Newsreader display serif, Instrument Sans UI, IBM Plex Mono metadata.

| Component | Description |
|-----------|-------------|
| `Alert` | Informational, warning, error, and success banners |
| `Badge` | Multi-tone pill labels (neutral, primary, success, warning, danger, info) |
| `Breadcrumbs` | Accessible `nav > ol > li` breadcrumb trail with `aria-current="page"` |
| `Button` | Primary, secondary, ghost, and danger variants; loading state |
| `Card` | Surface container with consistent padding and border radius |
| `CopyableId` | Monospace identifier with one-click copy |
| `DefinitionList` | Label/value pairs for detail panes |
| `EmptyState` | Illustrated or icon empty-state blocks |
| `Input` | Text, email, password, and URL inputs with accessible error linking |
| `Modal` | Accessible focus-trapped dialog |
| `PageHeader` | Page title + subtitle + optional action slot |
| `Pagination` | Page controls with ARIA labels |
| `RoleChip` | Colour-coded role pill (CANDIDATE / RECRUITER / ADMIN) |
| `Select` | Branded accessible combobox with keyboard support and a hidden native `<select>` fallback |
| `Skeleton` | Rectangular and text loading placeholders |
| `Spinner` | Accessible spinning indicator with `aria-label` |
| `StatusBadge` | Domain workflow statuses (application, job, recruitment stages) |
| `Table` | `TableContainer`, `Table`, `TableHeader`, `TableBody`, `TableRow`, `TableHead`, `TableCell`, `TableEmptyRow` |
| `Tabs` | WAI-ARIA tablist with arrow-key navigation, badge counts |
| `Textarea` | Multi-line input with accessible error linking |
| `Timeline` | Vertical event timeline for recruitment progress |

All components are barrel-exported from `src/components/ui/index.ts`.

## Authentication

- Access tokens stay **in memory only** (never `localStorage` / `sessionStorage`)
- Refresh token is an HttpOnly cookie managed by the browser
- Proactive refresh runs before access-token expiry via `AuthContext`
- On plain `http://localhost`, a `Secure` refresh cookie may be rejected — silent refresh then fails; re-login is expected
- Route guards enforce role menus in the UI; the Gateway and services enforce real authorization

## Roles and Routes

| Role | Home | Routes |
|------|------|--------|
| Public | `/jobs` | `/jobs`, `/jobs/:jobId`, `/login`, `/register` |
| `CANDIDATE` | `/candidate/applications` | applications list & detail, apply `/jobs/:jobId/apply`, progress, documents, notifications, profile |
| `RECRUITER` | `/recruiter/jobs` | jobs list, create `/recruiter/jobs/new`, detail & edit, recruitments list & detail, application lookup, profile |
| `ADMIN` | `/admin/overview` | overview (recruitment count), recruitments list, application lookup, profile |

- `/workspace` redirects to role home after auth check
- `/403`, `/404`, `/error`, `/offline`, `/session-expired` are standalone error pages
- Unknown paths redirect to `/404`

## API Surface

All calls go through the API Gateway (`/api/v1/*`):

| Endpoint prefix | Operations |
|-----------------|-----------|
| `/api/v1/auth/*` | login, register, refresh, logout, me |
| `/api/v1/jobs/*` | list (paginated, filterable), get, create, update, open / close / reopen |
| `/api/v1/applications/*` | list (candidate-scoped), get, create (apply), withdraw, status update (recruiter) |
| `/api/v1/recruitments/*` | list, get, create, schedule interview, update interview status, set final status |
| `/api/v1/documents/*` | list, upload, download (redirect), delete |
| `/api/v1/notifications/*` | list, get single |

## Accessibility

- Semantic HTML (`nav`, `main`, `section`, `article`, headings, `<button>` for interactive elements)
- `aria-label` on icon-only controls, `aria-current="page"` on breadcrumbs
- WAI-ARIA tablist pattern with arrow-key navigation in `Tabs`
- `aria-live` regions on toast notifications
- Visible focus rings on all interactive elements (`focus-ring` utility)
- Error messages associated to form fields via `aria-describedby`
- `aria-busy` / `aria-label` on loading spinners and skeletons
- `prefers-reduced-motion` respected globally

## Known Limitations

- Recruiter applicant review is scoped to a job; global lookup remains by application ID
- No password reset or profile-edit backend endpoints
- Admin overview shows only a verified recruitment count, not a full analytics dashboard
- Document download for other users is constrained by backend ownership rules
- AI features (ai-service) are out of scope for this frontend milestone

## Verification

From `frontend/`:

```bash
npm run lint        # 0 warnings
npm run typecheck   # 0 errors
npm run test:run    # 49/49 tests pass across 8 test files
npm run build       # clean production bundle (dist/)
```

All four must exit 0 before publishing frontend changes.

## Docker

Frontend containerisation is handled within the wider stack (later deployment phases). Local development uses `npm run dev` against a running Gateway.
