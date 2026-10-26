# SmartHire — Master Frontend Design Specification

**Project:** PS044-SmartHire (Project 44 — Online Job Recruitment System)
**Purpose:** Implementation-ready specification for generating the complete SmartHire frontend in Google Stitch.
**Backend baseline:** Inc 0+1+2+3 as committed in this repository (api-gateway, service-registry, auth, job, application, recruitment, document, notification services; ai-service present but unwired).
**Document status:** Documentation only. No application code is changed by this document.

---

## 0. How to read this document

Every factual claim carries one of four labels. Nothing in this document is unlabelled guesswork.

| Label | Meaning |
|---|---|
| **[V]** Verified | Read directly from source in this repository (controller, DTO, entity, security config, `application.yml`, or Flyway migration). Safe to build against. |
| **[UX]** Inferred UX | A design recommendation not dictated by the backend. Changeable without breaking any contract. |
| **[P]** Planned | Code exists but is not reachable by a browser (not gateway-routed, not deployed, or mock-only). Must be rendered as unavailable. |
| **[GAP]** Gap | The frontend needs this and the backend does not provide it. Must not be faked. See §13 for the full register. |

**Rule for the generating tool:** if a screen element is marked **[GAP]**, generate the screen with the documented *gap treatment* (disabled state + explanatory copy), never with invented data or invented endpoints.

**Source of truth precedence:** source code > OpenAPI annotations > `docs/architecture/*` > `README.md`. Where `README.md` or `docs/discovery/phase-2-product-model.md` disagrees with code, code wins and the divergence is recorded in §13.

---

## 1. Product overview, goals, scope, non-goals

### 1.1 What SmartHire is

A microservices job-recruitment platform. Candidates browse open job postings, apply with a résumé reference, upload documents, and track the outcome of their applications. Recruiters publish and manage postings for their organization, move applications through a review pipeline, and run an interview-and-decision workflow. A platform administrator has read-across visibility for operational support.

All browser traffic goes through a single public entry point: the **API Gateway on `http://localhost:8080`** [V]. No service is addressed directly by the frontend.

### 1.2 Goals

1. **G1 — Make the implemented workflow completable in a browser.** Register → login → browse → apply → recruiter review → shortlist → interview → decision → candidate sees outcome.
2. **G2 — Make authorization legible.** The user always knows which role they hold, what they may do, and why an action was refused (401 vs 403 vs 404-as-privacy).
3. **G3 — Make state machines visible, not guessable.** Job, application, interview and final status are strict state machines; the UI offers only legal transitions and explains refusals.
4. **G4 — Be honest about absent capability.** Planned AI features and backend gaps are shown as unavailable, never simulated.
5. **G5 — Survive a 15-minute access token.** Token expiry mid-session is a designed-for state, not an error.

### 1.3 In scope

- Public job browsing (anonymous).
- Candidate registration, login, session lifecycle.
- Candidate: apply, view one application, withdraw, upload/list/download/delete documents, view notifications, track recruitment progress.
- Recruiter: create/edit jobs, open/close/reopen jobs, view an application, change application status, shortlist into a recruitment, drive interview status, record final decision.
- Admin: identity confirmation, read-across recruitment listing, read-by-id lookups.
- Full error, empty, loading and validation state coverage.
- Responsive behaviour from 360 px to 1536 px+.

### 1.4 Explicit non-goals

| Non-goal | Reason |
|---|---|
Real AI scoring, résumé parsing, JD assistance | `ai-service` is mock-only, not gateway-routed, not in `deploy/docker-compose.yml` **[P]** |
Email inbox / real email delivery UI | Notification sender is `LoggingNotificationSender` **[V]**; no mail transport in compose |
Password reset, email verification, change password | No endpoints exist **[GAP-09]** |
Candidate profile editing (name, phone, avatar) | `User` entity holds only `email`, `role`, `orgId`, `createdAt` **[V]**; no update endpoint **[GAP-10]** |
Organization / recruiter / user administration screens | Candidates and recruiters self-register and administrators register with an invitation code, but there is no user, role, or organization administration API or screen **[GAP-03]** |
Multi-tenancy switching, org settings | Single `orgId` claim per user; no org API |
Offline mode, PWA, push notifications | Not in architecture |
Job description rich text, salary, employment type, skills | `Job` has only `companyName`, `role`, `location`, `status`, `ownerOrgId` **[V]**, **[GAP-11]** |
Server-side search of applications | No list endpoint **[GAP-01]** |
Interview scheduling with dates/times/calendar | `InterviewStatus` is an enum only — no date, no interviewer, no location field **[V]**, **[GAP-16]** |

---

## 2. Personas, permissions, navigation

### 2.1 Roles

Three roles exist in `com.smarthire.auth.domain.Role` **[V]**: `CANDIDATE`, `RECRUITER`, `ADMIN`.

- Public self-registration (`POST /api/v1/auth/register`) is **role-aware** **[V]**. `CANDIDATE` is the default when `role` is omitted; `CANDIDATE` and `RECRUITER` signup are open. `RECRUITER` requires `organizationName` (2–255 characters) and creates a dedicated organization so job ownership works immediately. `ADMIN` requires the deployment's `AUTH_ADMIN_SIGNUP_CODE`; administrator signup is closed when that value is blank.
- `DevDataSeeder` under the `seed` Spring profile remains available for seeded demo accounts, with credentials supplied from the environment **[V]**. Credentials for the demo accounts live in `README.md` §Review-1 Demo Setup and in the operator's local `deploy/.env` — **never render, log, prefill or hardcode them in the frontend.**
- `ADMIN` is a platform-operations role, **not** a recruitment decision-maker. `@PreAuthorize("hasRole('RECRUITER')")` on job and recruitment writes deliberately excludes ADMIN **[V]**.

The JWT carries `roles` as an **array** — the UI must treat it as a list, not a scalar **[V]**.

### 2.2 Identity available to the UI

`GET /api/v1/auth/me` returns `{ sub, email, roles[], orgId }` **[V]**. `orgId` is `null` for candidates and for an administrator created without an employer; a recruiter carries the organization created at registration, or the seeded demo organization for a seeded recruiter **[V]**.

This is the **only** identity source. There is no endpoint to resolve any *other* user's id to a name or email — see **[GAP-15]**. Consequence: every recruiter- and admin-facing screen can display a candidate only as a UUID.

### 2.3 Permission matrix (verified)

Legend: ✅ allowed · ❌ `403` · 🚫 `404` (deliberate privacy masking — resource exists but caller may not know) · — not applicable.

| Capability | Anonymous | CANDIDATE | RECRUITER | ADMIN |
|---|---|---|---|---|
`POST /auth/register` | ✅ | ✅ | ✅ org name | ✅ code |
`POST /auth/login` · `/refresh` | ✅ | ✅ | ✅ | ✅ |
`POST /auth/logout` | 401 | ✅ | ✅ | ✅ |
`GET /auth/me` | 401 | ✅ | ✅ | ✅ |
`GET /auth/admin/whoami` | 401 | ❌ | ❌ | ✅ |
`GET /jobs` (OPEN only) | ✅ | ✅ | ✅ | ✅ |
`GET /jobs?mine=true` | 401 | ❌ | ✅ own org | ❌ |
`GET /jobs` (+ own DRAFT/CLOSED) | — | — | ✅ | — |
`GET /jobs/{id}` OPEN | ✅ | ✅ | ✅ | ✅ |
`GET /jobs/{id}` DRAFT/CLOSED | 🚫 | 🚫 | ✅ own org · 🚫 other | 🚫 |
`POST /jobs`, `PUT /jobs/{id}` | 401 | ❌ | ✅ own org | ❌ |
`POST /jobs/{id}/open·close·reopen` | 401 | ❌ | ✅ own org | ❌ |
`POST /applications` | 401 | ✅ | ❌ | ❌ |
`GET /applications/{id}` | 401 | ✅ own · 🚫 other | ✅ own-org job · 🚫 other | ✅ any |
`POST /applications/{id}/withdraw` | 401 | ✅ own · ❌ other | ❌ | ❌ |
`PATCH /applications/{id}/status` | 401 | ❌ | ✅ own-org job | ✅ any |
`POST /recruitments` | 401 | ❌ | ✅ own-org application · 🚫 other | ❌ |
`GET /recruitments/{id}` | 401 | ✅ own · 🚫 other | ✅ own org · 🚫 other | ✅ any |
`GET /recruitments` | 401 | ✅ own (scoped) | ✅ own org (scoped) | ✅ all |
`PATCH /recruitments/{id}/interview-status` | 401 | ❌ | ✅ own org · 🚫 other | ❌ |
`PATCH /recruitments/{id}/final-status` | 401 | ❌ | ✅ own org · 🚫 other | ❌ |
`POST /documents` | 401 | ✅ | ❌ | ❌ |
`GET /documents` · `/type/{t}` | 401 | ✅ own | ❌ | ❌ |
`GET /documents/{id}` · `/{id}/content` | 401 | ✅ own · 🚫 other | 🚫 **always** | ✅ any |
`DELETE /documents/{id}` | 401 | ✅ own | ❌ | ❌ |
`GET /notifications` · `/{id}` | 401 | ✅ own | ✅ (returns empty) | ✅ (returns empty) |

Two rows deserve UI attention:

- **Recruiters can never read documents.** `DocumentService.get` returns 404 for any non-owner, non-admin caller; the code comment states recruiter access is deferred past R1 **[V]**. Design accordingly (§5.14, **[GAP-02]**).
- **`GET /notifications` is not role-gated.** It scopes by `jwt.sub`, so a recruiter or admin calling it gets their own (always empty) list rather than a 403 **[V]**. Do not surface a Notifications nav item for RECRUITER/ADMIN.

`POST /auth/register` is public for candidates and recruiters. An administrator request is accepted only with the deployment's invitation code; when the operator leaves `AUTH_ADMIN_SIGNUP_CODE` blank, administrator registration is unavailable. Recruiter signup additionally requires an organization name. The selected role at login is a UI intention only — the token role decides the workspace.

### 2.4 Navigation per persona

Navigation is **role-derived from `roles[]` in `GET /auth/me`**, never from a locally stored preference.

**Anonymous** — top bar only, no sidebar:
`Browse jobs` · `Sign in` · `Create account`

The landing page (`/`) presents one workspace entry point per role and a direct path to public job browsing. The sign-in and registration links carry the intended role as `?role=`, but the role remains a preference until the server returns the account's real roles.

**CANDIDATE** — sidebar:
1. `Browse jobs` → `/jobs`
2. `My applications` → `/candidate/applications` *(degraded, **[GAP-01]**)*
3. `My progress` → `/candidate/progress`
4. `My documents` → `/candidate/documents`
5. `Notifications` → `/candidate/notifications`
6. `Profile` → `/candidate/profile`

**RECRUITER** — sidebar:
1. `Job postings` → `/recruiter/jobs`
2. `Recruitments` → `/recruiter/recruitments`
3. `Look up application` → `/recruiter/applications` *(ID-entry utility, **[GAP-01]**)*
4. `Browse all jobs` → `/jobs`
5. `Profile` → `/recruiter/profile`

**ADMIN** — sidebar:
1. `Overview` → `/admin/overview`
2. `All recruitments` → `/admin/recruitments`
3. `Record lookup` → `/admin/lookup`
4. `Profile` → `/admin/profile`

**Persona switching does not exist.** A user holds the role their token says; there is no impersonation.

---

## 3. Sitemap and screen inventory

```
/                               S-00  Landing page (three workspaces)    [V]
/jobs                           S-01  Public job browse                  [V]
/jobs/:jobId                    S-02  Job detail (public)                [V]
/jobs/:jobId/apply              S-03  Apply to job (candidate)           [V]
/login                          S-04  Sign in (role selector)            [V]
/register                       S-05  Create account (role-aware)        [V]
/session-expired                S-06  Session expired interstitial       [UX]
/403                            S-07  Forbidden                          [V behaviour]
/404                            S-08  Not found                          [V behaviour]
/error                          S-09  Unexpected error boundary          [V behaviour]
/offline                        S-10  Network unreachable                 [UX]

CANDIDATE
/candidate/applications         S-11  My applications (degraded)         [GAP-01]
/candidate/applications/:id     S-12  Application detail                 [V]
/candidate/progress             S-13  My recruitment progress            [V]
/candidate/progress/:id         S-14  Recruitment progress detail        [V]
/candidate/documents            S-15  My documents                       [V]
    ·· dialog                   S-16  Upload document                    [V]
    ·· dialog                   S-17  Confirm delete document            [V]
/candidate/notifications        S-18  Notifications list                 [V]
/candidate/notifications/:id    S-19  Notification detail                [V]
/candidate/profile              S-20  Profile (read-only)                [V]

RECRUITER
/recruiter/jobs                 S-21  Job postings (org scope)           [V]
/recruiter/jobs/new             S-22  Create job posting                 [V]
/recruiter/jobs/:jobId          S-23  Job posting detail + lifecycle     [V]
/recruiter/jobs/:jobId/edit     S-24  Edit job posting                   [V]
/recruiter/applications         S-25  Application lookup by ID           [GAP-01]
/recruiter/applications/:id     S-26  Application review + status        [V]
    ·· dialog                   S-27  Confirm status transition          [V]
    ·· dialog                   S-28  Shortlist into recruitment         [V]
/recruiter/recruitments         S-29  Recruitments (org scope)           [V]
/recruiter/recruitments/:id     S-30  Recruitment workflow detail        [V]
    ·· dialog                   S-31  Record final decision              [V]
/recruiter/profile              S-32  Profile (read-only)                [V]

ADMIN
/admin/overview                 S-33  Admin overview                     [V, thin]
/admin/recruitments             S-34  All recruitments                   [V]
/admin/lookup                   S-35  Record lookup by ID                [V]
/admin/profile                  S-36  Profile (read-only)                [V]

PLANNED — generate as locked/unavailable, do not wire
/recruiter/ai/match-scores      S-37  AI match scores                    [P]
/recruiter/ai/jd-assist         S-38  AI job-description assistant       [P]
/candidate/ai/resume-insights   S-39  AI résumé insights                 [P]
```

**37 buildable screens + 3 locked planned screens.** The landing page is static; 31 of the other 36 are fully grounded in verified endpoints, and 5 carry a documented gap treatment (S-11, S-25, S-33, S-35 partial, S-20/32/36 read-only limitation).

---

## 4. Global layout and shell

### 4.1 App shell [UX]

```
┌──────────────────────────────────────────────────────────────┐
│ TOP BAR  h=56                                                │
│ [≡] SmartHire        {role chip}   {email}  [Sign out]       │
├────────────┬─────────────────────────────────────────────────┤
│ SIDEBAR    │ CONTENT                                         │
│ w=240      │  ┌───────────────────────────────────────────┐  │
│            │  │ PAGE HEADER                               │  │
│ nav items  │  │ Breadcrumb                                │  │
│ (role      │  │ H1 title           [primary action]       │  │
│  derived)  │  │ subtitle / meta                           │  │
│            │  ├───────────────────────────────────────────┤  │
│            │  │ PAGE BODY  max-w 1200, gutter 24          │  │
│ ─────────  │  └───────────────────────────────────────────┘  │
│ footer:    │                                                 │
│ API docs ↗ │                                                 │
└────────────┴─────────────────────────────────────────────────┘
  Toast region: fixed bottom-right, 16px inset, stacked, max 3
```

- **Top bar** is fixed, full width, `--c-surface` with a 1px bottom border. Shows the role chip (`Candidate` / `Recruiter` / `Admin`) so authorization is never ambiguous (G2).
- **Sidebar** 240 px, `--c-primary-900` background, light text. Collapses to a 64 px icon rail at ≤1024 px; becomes an overlay drawer at ≤768 px triggered by `[≡]`.
- **Content** scrolls independently. Page body max-width 1200 px, centred, 24 px gutters (16 px at ≤640 px).
- **Footer link** `API documentation ↗` → `http://localhost:8080/swagger-ui.html` (gateway aggregates all six service specs **[V]**). Show for RECRUITER and ADMIN only **[UX]**.

### 4.2 Public shell [UX]

No sidebar. Top bar carries `SmartHire` wordmark, `Browse jobs`, and right-aligned `Sign in` / `Create account`. Content max-width 1120 px.

### 4.3 Auth shell [UX]

Single centred card, 400 px wide, vertically centred with a 64 px minimum top offset. Wordmark above the card. No sidebar, no top bar nav.

### 4.4 Responsive rules

| Breakpoint | Shell | Tables | Forms | Page header |
|---|---|---|---|---|
≥1280 `xl` | Sidebar 240 | Full columns | 2-col where specified | Title + action inline |
1024–1279 `lg` | Sidebar 240 | Full columns | 2-col | Title + action inline |
768–1023 `md` | Icon rail 64 | Drop lowest-priority columns (§6.6) | 1-col | Title + action inline |
640–767 `sm` | Drawer overlay | **Card list** replaces table | 1-col | Action below title, full-width |
360–639 `xs` | Drawer overlay | **Card list** | 1-col, inputs full-width | Stacked; sticky bottom action bar for primary submit |

Minimum supported width **360 px**. No horizontal page scroll at any breakpoint; wide data switches to the card-list pattern instead of scrolling.

---

## 5. Page-by-page specifications

Each spec gives: purpose · access · data · layout · actions · states · validation. Endpoint detail is in §9; it is referenced here by name.

---

### S-00 · Landing page — `/`

**Purpose** Explain SmartHire and give each visitor a direct path into the right workspace without hiding public job browsing.
**Access** Public.
**Data** None — static copy plus links. No authenticated request is needed to render the page.

**Layout** Public shell.
```
Hero:  "Hiring decisions, on the record"
       [Browse open roles]  [Open my workspace]
Three workspace cards:
  Candidate      → [Create candidate account]  [Browse open roles]
  Recruiter      → [Create recruiter account]  [Recruiter sign in]
  Administrator  → [Administrator sign in]      (registration link when invited)
Trust strip: candidate owned · recruiter reviewed · administrator read-only
```

**Rules**
- Workspace links use `?role=` so `/login` and `/register` open with the intended portal selected. The selection is intent only; the token role still decides where the account lands.
- A signed-in visitor can use `Open my workspace`, which resolves through `/workspace`.
- `Browse open roles` is always visible and never requires an account.
- Do not render seeded credentials, administrator invitation codes, or account counts on this page.

---

### S-01 · Public job browse — `/jobs`

**Purpose** Entry point. Discover OPEN postings without an account.
**Access** Anonymous ✅. Authenticated users also land here from `Browse jobs`. **[V]** — gateway permits `GET /api/v1/jobs/**` anonymously.
**Data** `GET /jobs` → `PagedModel<JobResponse>`: `content[{ jobId, companyName, role, location, status, ownerOrgId }]` + `page{ size, number, totalElements, totalPages }` **[V]**.

**Layout**
```
PAGE HEADER   H1 "Open roles"   subtitle "{totalElements} postings"
FILTER BAR    [ q: search company or role ] [ location ] [ status ▾ ] [Clear]
RESULT LIST   JobCard ×n   (grid: 1 col ≤767, 2 col 768–1279, 3 col ≥1280)
PAGINATION    « ‹ 1 2 3 › »   [size ▾ 10|20|50|100]
```

**JobCard** — `role` as 18/600 heading · `companyName` 14/500 muted · `location` 13 with pin icon · StatusBadge top-right · whole card is the link to `/jobs/:jobId` · hover raises shadow and shifts border to `--c-border-strong`.

**Filters [V]**
- `q` — free text, matched server-side against company name **and** role. Debounce 300 ms **[UX]**. Server clamps filter length; keep the input `maxlength=200` **[UX]**.
- `location` — free text, server does a contains match. **Not** a dropdown: there is no distinct-locations endpoint **[GAP-14]**.
- `status` — select. **Anonymous and candidates: hide this control entirely** (only OPEN is ever returned to them, so the filter would imply false capability). **Recruiters: show `OPEN · DRAFT · CLOSED`** — a recruiter's list legitimately includes their own org's DRAFT and CLOSED rows **[V]**.
- `page` default 0, `size` default 20, **server hard-clamps size to 100** — the size selector must not offer a larger value **[V]**.
- Sort is fixed server-side to `jobId`. **Do not render sortable column headers** — they would not work **[V]**, **[GAP-13]**.

**Filter state lives in the URL query string** so a filtered list is shareable and survives reload **[UX]**.

**States**
- *Loading* — 6 JobCard skeletons, preserve grid.
- *Empty, no filters* — illustration + “No open roles right now” + “Check back soon.”
- *Empty, with filters* — “No roles match these filters” + `[Clear filters]`.
- *Error 5xx* — inline `ErrorPanel` with `[Retry]` and the `correlationId` in a copyable monospace chip.
- *429* — rate-limit banner (§8.8).

---

### S-02 · Job detail — `/jobs/:jobId`

**Purpose** Full posting; launch point for applying.
**Access** OPEN → anyone. DRAFT/CLOSED → owning-org recruiter only; everyone else receives **404, not 403** **[V]**. Render the standard Not-found page — do not hint that the id exists.
**Data** `GET /jobs/{jobId}` → `JobResponse` **[V]**.

**Layout**
```
Breadcrumb  Jobs / {role}
H1 {role}                                   [Apply now]     ← primary, right
Meta row    {companyName} · {location} · StatusBadge({status})
─────────────────────────────────────────────────────────────
Two columns ≥1024 (8/4 split), stacked below
LEFT  "About this role" card
      ⚠ The backend stores no description, salary, or employment type.
        Render a DefinitionList of the four real fields only.           [GAP-11]
      DefinitionList: Company · Role · Location · Status
RIGHT "Apply" card
      status OPEN      → [Apply now] + "Open for applications"
      status CLOSED    → disabled button + "This posting is closed."
      status DRAFT     → disabled button + "Not yet published." (recruiter view only)
      anonymous        → [Sign in to apply] → /login?next=/jobs/:id/apply
      RECRUITER/ADMIN  → no apply affordance; recruiters get [Manage posting]
                         → /recruiter/jobs/:id when ownerOrgId === own orgId
```

**Do not invent a job description.** A placeholder paragraph of marketing copy here would be fabricated content. Use the gap note pattern from §6.12.

**States** Loading → skeleton of header + two cards. 404 → S-08. 5xx → S-09.

---

### S-03 · Apply to job — `/jobs/:jobId/apply`

**Purpose** Submit an application.
**Access** `CANDIDATE` only. Recruiters/admins → 403 (§8.3). Anonymous → redirect to `/login?next=…` **[V]**.
**Data in** `POST /applications` body `{ jobId: UUID, resumeLink: string }` **[V]**. `candidateId` is taken from the verified JWT `sub` — **never send it** **[V]**.
**Data out** `201` + `Location: /api/v1/applications/{id}` + `ApplicationResponse` **[V]**.

**Layout** Single centred form card, 560 px.
```
Breadcrumb  Jobs / {role} / Apply
H1 "Apply for {role}"
Summary strip (read-only): {companyName} · {location} · StatusBadge
─────────────────────────────────────────────
Field  Résumé link *            [ url input, maxlength 2048 ]
       helper: "A publicly reachable link to your résumé (PDF or DOCX)."
       [ ▾ Use one of my uploaded documents ]      ← see note below
Field  (none — the API accepts no cover letter, no answers)       [GAP-12]
─────────────────────────────────────────────
[Cancel]                                   [Submit application]
```

**Résumé link is a free-text URL, not a document reference.** `Application.resumeLink` is an unconstrained `String` (max 2048) with no server-side link to `document_svc` **[V]**, **[GAP-05]**. The optional `Use one of my uploaded documents` disclosure is a **client-side convenience only**: it lists the candidate's documents from `GET /documents` and, on pick, writes the gateway content URL (`{baseUrl}/api/v1/documents/{documentId}/content`) into the text field. State plainly in the helper text that this URL requires the viewer to be signed in as the owner — **a recruiter following it will receive 404** **[GAP-02]**. Prefer offering it with that caveat over pretending the link is portable.

**Validation**
| Field | Client rule (mirrors server) | Message |
|---|---|---|
`resumeLink` | required, non-blank, ≤2048 chars | “A résumé link is required.” / “Links must be 2048 characters or fewer.” |
`resumeLink` | **[UX]** must parse as `http(s)://…` | “Enter a complete URL starting with http:// or https://” — client-side only; the server accepts any non-blank string **[V]** |
`jobId` | present (from route) | — |

**Error mapping [V]**
| Status | Server cause | UI |
|---|---|---|
`400` | Bean Validation failure | Field-level errors from `detail` |
`404` | Job id unknown to job-service | Banner “This posting no longer exists.” + `[Back to jobs]` |
`409` | Job not `OPEN` (`"Cannot apply to a job that is not OPEN; current status: X"`) | Banner “This posting is no longer accepting applications.” Refresh the job and swap the panel to its closed state |
`403` | Caller is not CANDIDATE | Redirect to `/403` |
`429` | Gateway rate limit (20 rps / 40 burst per user) | §8.8 |
`503` | `JobServiceUnavailableException` | Banner “We couldn't verify this posting right now. Please try again.” + `[Retry]` |

**Success** Toast “Application submitted.” → navigate to `/candidate/applications/{applicationId}` (id from the response body).
**Critical:** persist the returned `applicationId` into the client's local application registry (§5.S-11) — it is the only way the candidate will find this application again before it is shortlisted **[GAP-01]**.

**Duplicate applications are not prevented server-side.** There is no uniqueness constraint on `(job_id, candidate_id)` **[V]**, **[GAP-07]**. Before submitting, check the local registry and, on a match, show a non-blocking warning: “You already applied to this role on {date}. Submitting again will create a second application.” with `[Submit anyway]` / `[Cancel]`.

---

### S-04 · Sign in — `/login`

**Access** Public **[V]**.
**Data** `POST /auth/login` `{ email, password }` → `200 { tokenType:"Bearer", accessToken, accessTokenExpiresAt }` + `Set-Cookie: smarthire_refresh` **[V]**. The role selector is a client-side intention and is **not** sent to `/login`.

**Layout** Auth shell card.
```
Wordmark
H1 "Sign in"
Role selector: Candidate · Recruiter · Administrator   → ?role=
[ Email      ]  type=email, autocomplete=username
[ Password   ]  type=password, autocomplete=current-password, [show/hide]
( no "remember me" — session length is server-fixed )
[ Sign in ]  full-width primary
──────────────────────────────
"New to SmartHire?  Create a {role} account →"  /register?role={role}
```

**Role intent and mismatch** The selector exists so each role has a clear entry point and role-specific copy. It never grants access. After login, compare the selected role with the `roles[]` returned by the server:
- A generic `/login` link (no `?role=`) **must not** show a mismatch notice.
- If the visitor explicitly chose `?role=` and the account does not hold it, open the account's real workspace and show a dismissible notice: “Signed in to the {actual} workspace — this account is registered as {actual}, so we opened that portal instead of the {selected} portal.”
- Never reject the credentials or end the session because the selector was wrong.

**Omit "Forgot password?"** — no such endpoint exists **[GAP-09]**. A dead link is worse than its absence.

**Validation** Both fields required. `email` ≤255, `password` ≤128 **[V]**. Do **not** apply the 12-character minimum here — that rule belongs to registration only; applying it at login would block legitimate seeded accounts **[V]**.

**Errors [V]**
| Status | UI |
|---|---|
`401` | Form-level alert: “Incorrect email or password.” Never say which was wrong. Clear the password field, keep email, focus password. |
`400` | Field errors. |
`429` | Gateway limits `/api/v1/auth/**` to **10 rps / 20 burst per client IP** **[V]**. Alert: “Too many attempts. Wait a moment and try again.” Disable submit for 10 s with a visible countdown **[UX]**. |
`503` | “Sign-in is temporarily unavailable.” + `[Retry]` |

**On success** Store `accessToken` **in memory only** (never `localStorage`/`sessionStorage`) per ADR-0014 and architecture §6 **[V]**. Store `accessTokenExpiresAt` to schedule silent refresh (§8.6). Then `GET /auth/me`, and route by role:
`CANDIDATE → /candidate/applications` · `RECRUITER → /recruiter/jobs` · `ADMIN → /admin/overview` · honour `?next=` when present and permitted.

> **Local-development caveat, surface it to the operator [V]:** the refresh cookie is set with `Secure` and `Path=/api/v1/auth`. Over plain `http://localhost` a browser will not store it, so silent refresh and `POST /auth/refresh` will fail and the session ends hard at 15 minutes. The frontend must therefore treat "refresh unavailable" as a first-class path (§8.6), not an exception. See **[GAP-08]**.

---

### S-05 · Create account — `/register`

**Access** Public **[V]**. The role selector chooses `CANDIDATE` (default), `RECRUITER`, or `ADMIN`. Candidate and recruiter signup are open; administrator signup requires the deployment invitation code **[V]**.
**Data** `POST /auth/register` `{ email, password, role?, organizationName?, adminInviteCode? }` → `201 { userId }` **[V]**.

**Layout** Auth shell card.
```
H1 "Create an account"
Subtitle: role-specific (candidate / recruiter / administrator)
Role selector: Candidate · Recruiter · Administrator   → ?role=
[ Email           ]  type=email, autocomplete=username
role=RECRUITER:
[ Organization name ]  required, 2–255, autocomplete=organization
role=ADMIN:
[ Administrator invitation code ]  type=password, required
[ Password        ]  type=password, autocomplete=new-password, [show/hide]
   PasswordStrengthMeter + live rule checklist
[ Confirm password]  client-side only
[ Create {role} account ]  full-width primary
──────────────────────────────
"Already have an account?  Sign in →"  /login?role={role}
```

**Validation — mirror the server exactly [V]**
| Field | Rule | Source |
|---|---|---|
`email` | required · RFC-valid (`@Email`) · ≤255 | `RegisterRequest` |
`password` | required · **≥12** · ≤128 | `RegisterRequest` `@Size(min=12,max=128)` |
`confirmPassword` | must equal `password` | **[UX]** client-only |
`role` | optional · one of `CANDIDATE`, `RECRUITER`, `ADMIN`; omitted means `CANDIDATE` | `@Pattern` |
`organizationName` | required for `RECRUITER` (2–255 after trimming); rejected for other roles | `AuthService` |
`adminInviteCode` | required for `ADMIN` (≤256); absent or wrong is denied | `RegistrationPolicy` |

The live checklist shows exactly one server-enforced rule — “At least 12 characters” — plus **[UX]** advisory items (mixed case, a number, a symbol) rendered as *suggestions*, visually distinct from the enforced rule. Do not block submission on advisory items; the server does not.

**Errors [V]**
| Status | UI |
|---|---|
`409` | Field error on email: “An account with this email already exists.” + inline `Sign in instead →`. (`AuthExceptionHandler` maps `EmailAlreadyExistsException` → `409`.) |
`400` | Field errors parsed from `detail`; includes a missing recruiter organization name or an organization supplied for a non-recruiter role. |
`403` | Administrator-only: “Administrator registration is closed, or the invitation code is invalid.” Never echo the submitted code. |
`429` | Per-IP limit; same treatment as S-04. |

**On success** The endpoint returns only `userId` — **no token**. Do **not** attempt auto-login from the response. Navigate to `/login?role={role}` with a success banner: “Account created. Sign in with the credentials you just created.” Preserve the email and any `next` destination across the handoff. [V]

---

### S-11 · My applications — `/candidate/applications` **[GAP-01]**

**Purpose** The candidate's home. Lists their applications.
**Access** `CANDIDATE`.

> **This screen is the single largest gap.** There is **no** `GET /api/v1/applications` list endpoint. `ApplicationController` exposes only `POST /`, `GET /{id}`, `POST /{id}/withdraw`, `PATCH /{id}/status` **[V]**. `ApplicationService.listForCandidate` and `ApplicationRepository.findByCandidateId` both exist but are not wired to any controller method **[V]**. A candidate therefore cannot ask the server for their own applications.

**Grounded composition strategy — build this, do not fake a list endpoint:**

1. **Primary source — recruitment-derived rows.** `GET /recruitments` is candidate-scoped server-side and returns `{ recruitmentId, applicationId, jobId, candidateId, interviewStatus, finalStatus }` **[V]**. Every application that has been shortlisted into a recruitment appears here, authoritatively, on any device.
2. **Secondary source — local registry.** `applicationId`s captured at submit time (S-03) in browser storage, each re-validated with `GET /applications/{id}` **[V]** (authoritative status, drops anything stale or withdrawn).
3. **Merge** on `applicationId`, de-duplicate, then enrich each row with `GET /jobs/{jobId}` for company/role/location **[V]**.

**Layout**
```
H1 "My applications"
GapNotice (dismissible, persists dismissal)
  "This list is assembled from your tracked applications on this device plus any
   application that has entered a recruitment process. Applications submitted on
   another device may not appear until a recruiter progresses them."
  [ Add an application by ID ]   ← dialog accepting a UUID, validated via GET /applications/{id}
─────────────────────────────────────────────────────────
Filter chips: All · Active · Decided            ← client-side only
ApplicationTable  (card list ≤767)
  Role & company | Location | Status | Source | Last checked | →
PAGINATION  client-side
```

- **Status** `StatusBadge(application.status)` from the authoritative `GET /applications/{id}`.
- **Source** small muted chip: `Recruitment` (server-confirmed) or `This device` (local registry). This is honest about provenance and is why the gap notice is believable rather than alarming.
- **Active** = `APPLIED · UNDER_REVIEW · SHORTLISTED`; **Decided** = `HIRED · REJECTED · WITHDRAWN` **[V]**.

**States**
- *Loading* — 5 row skeletons.
- *Empty* — “No applications yet” + “Browse open roles to get started.” + `[Browse jobs]`.
- *Partially degraded* — if any `GET /applications/{id}` fails with 5xx, render the row with a warning icon and `[Retry]` rather than hiding it.
- *Local-registry entry now 404/403* — silently drop and remove from the registry.

**When [GAP-01] is closed** (`GET /api/v1/applications?page&size` scoped to the caller), replace the whole composition with a single paginated call, delete the gap notice and the Source column, and enable server-side status filtering. Design the table so this is a data-layer swap only.

---

### S-12 · Application detail — `/candidate/applications/:applicationId`

**Access** Owning candidate (others → 404) **[V]**.
**Data** `GET /applications/{id}` → `{ applicationId, jobId, candidateId, resumeLink, status }` **[V]**; plus `GET /jobs/{jobId}` for context **[V]**.

**Layout**
```
Breadcrumb  My applications / {role}
H1 {role}            StatusBadge({status})            [Withdraw application]
Meta  {companyName} · {location}
──────────────────────────────────────────────────────────────
LEFT (8)
  StatusTimeline — the application state machine, actual position marked
    APPLIED → UNDER_REVIEW → SHORTLISTED → HIRED
                                        ↘ REJECTED
    (WITHDRAWN shown as a terminal off-ramp when reached)
  "Your submission" card
    DefinitionList: Application ID (CopyableId) · Résumé link (external ↗)
RIGHT (4)
  "What happens next" card — plain-language copy per current status (table below)
  "Linked recruitment" card
    If a recruitment exists for this application → [View progress] → /candidate/progress/:recruitmentId
    Else → muted "No interview process has started yet."
```

**“What happens next” copy, keyed to the real enum [V][UX]**
| Status | Copy |
|---|---|
`APPLIED` | “Your application has been received and is waiting to be reviewed.” |
`UNDER_REVIEW` | “A recruiter is reviewing your application.” |
`SHORTLISTED` | “You've been shortlisted. The recruiter may schedule an interview next.” |
`HIRED` | “Congratulations — you've been hired for this role.” |
`REJECTED` | “This application was not taken forward.” |
`WITHDRAWN` | “You withdrew this application.” |

**Withdraw action [V]**
- Visible only when status ∈ `APPLIED · UNDER_REVIEW · SHORTLISTED` (the exact withdrawable set in `Application.withdraw()`). Hidden — not disabled — for `HIRED · REJECTED · WITHDRAWN`.
- `ConfirmDialog`: title “Withdraw this application?”, body “This cannot be undone. The recruiter will no longer consider you for {role}.”, destructive confirm labelled `Withdraw application`.
- `POST /applications/{id}/withdraw` → `200` updated `ApplicationResponse`. Toast “Application withdrawn.” Re-render from the response.
- `409` → “This application can no longer be withdrawn.” + refresh. `403` → `/403`.

**There is no "Linked recruitment" lookup endpoint.** Resolve it by scanning the candidate-scoped `GET /recruitments` page set for a matching `applicationId` **[V]**. If not found, show the muted state — do not assert that none exists beyond page 1 without paging through.

---

### S-13 · My recruitment progress — `/candidate/progress`

**Access** `CANDIDATE`; server scopes to `candidateId = jwt.sub` **[V]**.
**Data** `GET /recruitments?page&size` → `PagedModel<RecruitmentResponse>` **[V]**.

**Layout**
```
H1 "My progress"   subtitle "{totalElements} processes"
Table (card list ≤767)
  Role & company (via GET /jobs/{jobId}) | Interview | Decision | →
Pagination (server-side; size ≤100 clamped)
```
`Interview` = `StatusBadge(interviewStatus)`; `Decision` = `StatusBadge(finalStatus)`. The two lifecycles are independent — never collapse them into one column **[V]**.

**Empty** “No interview processes yet” + “When a recruiter shortlists one of your applications, it will appear here.”
No filters: the endpoint accepts only `page` and `size` **[V]**. Do not render filter controls that cannot work.

---

### S-14 · Recruitment progress detail — `/candidate/progress/:recruitmentId`

**Access** Candidate of record; others → 404 **[V]**.
**Data** `GET /recruitments/{id}` **[V]**. Note `ownerOrgId` is deliberately absent from the response — never display an organization id here **[V]**.

**Layout** Read-only. Two stacked `StatusTimeline`s:
```
Interview   NOT_SCHEDULED → SCHEDULED → COMPLETED
                                      ↘ NO_SHOW
Decision    PENDING → HIRED
                    ↘ REJECTED
```
Below: DefinitionList — Recruitment ID · Application ID (links to S-12) · Job (links to S-02).
**No dates are available** for any interview — `InterviewStatus` is an enum with no timestamp **[V]**, **[GAP-16]**. Do not render a date, time, location, or interviewer. A muted line is correct: “Interview scheduling details are shared outside the platform.”

---

### S-15 · My documents — `/candidate/documents`

**Access** `CANDIDATE` **[V]**.
**Data** `GET /documents?page&size` → **Jackson-serialized `Page<DocumentResponse>`** (default size 20) **[V]** — note this envelope differs from the `PagedModel` used by jobs/recruitments; see §9.9 and **[GAP-13]**.
`DocumentResponse`: `{ documentId, candidateId, documentType, fileName, contentType, fileSize, createdAt, updatedAt }` **[V]**.

**Layout**
```
H1 "My documents"                                   [Upload document]
TypeTabs  All · Résumé · Cover letter · Portfolio · Certificate · Other
DocumentTable (card list ≤767)
  File name (+ type icon) | Type | Size | Uploaded | Actions
    actions: [Download] [Delete]
Pagination (server-side)
```
- **TypeTabs** — `All` uses `GET /documents` (paginated); a specific type uses `GET /documents/type/{documentType}` which returns an **unpaginated `List`** **[V]**. Hide the pagination control while a type tab is active. The path segment is case-insensitive server-side (`DocumentType.parse`) **[V]**; send the canonical upper-case value anyway.
- **Size** formatted from `fileSize` bytes → `KB`/`MB`, 1 decimal.
- **Uploaded** `createdAt` as `d MMM yyyy, HH:mm` in local time; absolute value in a tooltip.
- **Download** → `GET /documents/{id}/content`. The response carries `Content-Disposition: inline` and the stored content type **[V]**. Because the request needs the `Authorization` header, **fetch as a blob and trigger a client-side download** — a plain `<a href>` will 401 **[V]**.

**Empty** “No documents yet” + “Upload your résumé so it's ready when you apply.” + `[Upload document]`.

---

### S-16 · Upload document (dialog) **[V]**

**Data** `POST /documents`, `multipart/form-data`, **two parts with exact names** **[V]**:
- `metadata` — a JSON **part**: `{"documentType":"RESUME"}` (`@RequestPart("metadata")`)
- `file` — the binary (`@RequestParam("file")`)

**Layout** Modal, 520 px.
```
H2 "Upload document"
[ Document type ▾ ]  RESUME · COVER_LETTER · PORTFOLIO · CERTIFICATE · OTHER
FileDropzone
   "Drag a file here or browse"
   "PDF, DOC or DOCX · up to 10 MB"
   accept=".pdf,.doc,.docx,application/pdf,application/msword,
           application/vnd.openxmlformats-officedocument.wordprocessingml.document"
Selected-file row: icon · name (truncate middle) · size · [×]
Progress bar during upload
[Cancel]                                        [Upload]
```

**Client validation — must mirror the server exactly (§10)**
| Rule | Server source | Message |
|---|---|---|
Type required | `@NotNull documentType` **[V]** | “Choose a document type.” |
File required | `MissingServletRequestParameterException` → 400 **[V]** | “Choose a file to upload.” |
Non-empty | `fileSize <= 0` → 400 **[V]** | “This file is empty.” |
≤ 10 MB | `smarthire.document.upload.max-file-size` = `${DOC_MAX_FILE_SIZE:10MB}` **[V]** | “Files must be 10 MB or smaller.” |
Allowed MIME | `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` **[V]** | “Only PDF, DOC and DOCX files are supported.” |

**Errors [V]** `400` → inline field error. `413` (`MaxUploadSizeExceededException`) → “This file is too large. The limit is 10 MB.” `403` → close and route to `/403`. `429` (10 rps / 20 burst per user) → §8.8. `500` (`IOException`, storage failure) → “We couldn't store this file. Please try again.” + `[Retry]`.

**Success** Close, toast “{fileName} uploaded.”, refetch the list, and flash-highlight the new row for 2 s **[UX]**.

---

### S-17 · Confirm delete document (dialog) **[V]**

`DELETE /documents/{id}` → `204`; owning candidate only **[V]**.
Destructive `ConfirmDialog`: “Delete {fileName}?” / “This permanently removes the file. Applications that link to it will have a broken résumé link.” / confirm `Delete` in danger style.
`404` → “This document no longer exists.” + refetch. `403` → `/403`. Success → toast “Document deleted.” + optimistic row removal with rollback on failure.

---

### S-18 · Notifications — `/candidate/notifications`

**Access** Any authenticated user; scoped to `jwt.sub` **[V]**. Surface in nav for CANDIDATE only (recruiters/admins get an empty list, not a 403) **[V]**.
**Data** `GET /notifications?page&size` → **HATEOAS `PagedModel<EntityModel<NotificationResponse>>`** with `_embedded` + `_links` **[V]** — a *third* pagination envelope **[GAP-13]**.
`NotificationResponse`: `{ notificationId, candidateId, applicationId, eventStatus, recipientEmail, subject, message, deliveryStatus, createdAt, sentAt, errorMessage }` **[V]**.

**Layout**
```
H1 "Notifications"
List of NotificationRow (not a table — message text needs width)
  ┌────────────────────────────────────────────────────────┐
  │ {subject}                        {relative createdAt}  │
  │ {message, clamped to 2 lines}                          │
  │ chip({eventStatus})   DeliveryChip({deliveryStatus})   │
  └────────────────────────────────────────────────────────┘
Pagination
```

- **There is no read/unread state** — the entity has no such column **[V]**, **[GAP-04]**. Do **not** render unread dots, bold-unread styling, a badge count, or a `Mark all read` action. All of those would be fabricated state.
- `eventStatus` is the application status that triggered the notification: one of `UNDER_REVIEW · SHORTLISTED · HIRED · REJECTED` **[V]**. Reuse the application `StatusBadge` palette.
- `DeliveryChip(deliveryStatus)` — `PENDING` neutral · `SENT` success · `FAILED` danger **[V]**. On `FAILED`, show a muted line “Delivery failed” and expose `errorMessage` on the detail screen only.
- `recipientEmail` is the user's own address; show it on detail only, never in the list **[UX]** (needless PII repetition).

**Empty** “No notifications” + “You'll be notified when a recruiter reviews or decides on an application.”

---

### S-19 · Notification detail — `/candidate/notifications/:id`

`GET /notifications/{id}` → `EntityModel<NotificationResponse>` **[V]**. Non-owner → **403** (not 404 — this endpoint differs from the rest of the system) **[V]**; render `/403`. Missing → 404.
Layout: `H1 {subject}` · full `{message}` · DefinitionList (Triggered by `eventStatus` · Delivery `deliveryStatus` · Created `createdAt` · Sent `sentAt` or “—” · Sent to `recipientEmail`) · on `FAILED` an `ErrorPanel` with `errorMessage` · `[View application]` → `/candidate/applications/{applicationId}`.

---

### S-20 / S-32 / S-36 · Profile (read-only) — `/{role}/profile`

**Data** `GET /auth/me` → `{ sub, email, roles[], orgId }` **[V]**.
```
H1 "Profile"
Card  DefinitionList
      Email          {email}
      Role           RoleChip per entry in roles[]
      User ID        CopyableId({sub})
      Organization   CopyableId({orgId})  or  "—  Not linked to an organization"
Card  "Session"
      Access token expires   {countdown}      [Refresh now]
      [Sign out]   [Sign out of this device]  ← same action, one button
GapNotice "Profile editing and password changes are not supported in this release."
```
`orgId` is `null` for candidates and the seeded admin **[V]** — render the em-dash row, not a blank.
**No edit affordance anywhere** — no update endpoint exists **[GAP-10]**, and no password change **[GAP-09]**. The gap notice replaces the buttons a user would otherwise hunt for.

---

### S-21 · Job postings — `/recruiter/jobs`

**Access** `RECRUITER`.
**Data** `GET /jobs?mine=true&status&location&q&page&size` **[V]**. `mine=true` restricts the result to the caller's own organization on the server. Without it the recruiter's result set is *all OPEN jobs across every organization* **plus** their own org's `DRAFT` and `CLOSED` **[V]**. There is still no `ownerOrgId` parameter, so visibility cannot be widened by a query string **[V]**.

**Layout**
```
H1 "Job postings"                                    [Create posting]
ScopeToggle   ( • My organization )  ( All open roles )     ← SERVER-SIDE via mine=true
FILTER BAR  [ q ] [ location ] [ status ▾ DRAFT|OPEN|CLOSED ] [Clear]
JobTable (card list ≤767)
  Role | Company | Location | Status | Actions
    actions per status:  DRAFT → [Open]   OPEN → [Close]   CLOSED → [Reopen]
                         always → [Edit] [View]
Pagination (server-side)
```

> **The scope toggle is a server filter.** `My organization` sends `mine=true`, so paging and total counts describe only the caller's organization. `All open roles` omits the flag and returns the public-open set plus the caller's own draft/closed postings. Do not re-filter a page in the browser. **[V]**

**Row actions** are status-conditional and reflect the exact legal transitions in `Job` **[V]**:
`DRAFT → open()` · `OPEN → close()` · `CLOSED → reopen()`. Render **only** the one legal action; never show all three greyed.
Each is an immediate `POST`, wrapped in a `ConfirmDialog` only for `Close` (it stops new applications) **[UX]**.
`Edit`/lifecycle actions must be hidden for rows where `ownerOrgId !== orgId` — those would 403 **[V]**.

**Empty** (My organization, no filters) — “No postings yet” + “Create your first job posting.” + `[Create posting]`.

---

### S-22 · Create job posting — `/recruiter/jobs/new`

**Access** `RECRUITER` **[V]**. ADMIN → 403 **[V]**.
**Data** `POST /jobs` `{ companyName, role, location }` → `201` + `Location` + `JobResponse` with `status: "DRAFT"` **[V]**. `ownerOrgId` comes from the token — **never send it** **[V]**.

**Layout** Form card, 640 px, single column.
```
Breadcrumb  Job postings / New
H1 "Create job posting"
Callout (info): "New postings are created as DRAFT. Publish it to make it visible
                 to candidates."
[ Company name * ]  maxlength 255
[ Role *         ]  maxlength 255
[ Location *     ]  maxlength 255
──────────────────────────────────────
[Cancel]        [Save as draft]   [Save and publish]
```

**Validation [V]** — all three: `@NotBlank`, `@Size(max=255)`.
`"Company name is required."` / `"Company name must be 255 characters or fewer."` (same shape for the other two). Show a live `{n}/255` counter past 200 characters **[UX]**.

**`Save and publish`** is a two-call sequence: `POST /jobs` then `POST /jobs/{id}/open` **[V]**. If the second call fails, **do not discard the first** — the draft exists. Navigate to S-23 and show a warning banner: “Posting saved as a draft, but publishing failed. {reason}” + `[Try publishing again]`. This honesty matters: a rollback is impossible here (no delete-job endpoint **[GAP-18]**).

**Errors** `400` field errors · `403` → `/403` · `503` → retry banner.
**Success** Toast “Posting created.” → `/recruiter/jobs/{jobId}`.

---

### S-23 · Job posting detail — `/recruiter/jobs/:jobId`

**Access** Owning-org `RECRUITER`; other orgs' `DRAFT`/`CLOSED` → 404 **[V]**.
**Data** `GET /jobs/{jobId}` **[V]**.

**Layout**
```
Breadcrumb  Job postings / {role}
H1 {role}   StatusBadge({status})          [Edit]  [{legal lifecycle action}]
Meta  {companyName} · {location}
──────────────────────────────────────────────────────────────
LEFT (8)
  "Lifecycle" card
     JobStatusTimeline:  DRAFT → OPEN ⇄ CLOSED
     Current position marked; the single legal next move rendered as the CTA
  "Applications" card                                          [GAP-01]
     GapNotice: "Listing applications for a posting is not yet available in the
                 API. Open an application directly by its ID."
     [ Application ID ___________________ ] [Open]
RIGHT (4)
  "Details" DefinitionList: Company · Role · Location · Status
                            Job ID (CopyableId) · Owner org (CopyableId)
  "Visibility" card
     DRAFT  → "Not visible to candidates."
     OPEN   → "Visible to everyone, including people who aren't signed in."
     CLOSED → "Visible to your organization only. Not accepting applications."
     (grounded in JobService.visibleTo + Job.isPubliclyVisible)             [V]
```

**Lifecycle CTA** — exactly one, label and confirmation by current status **[V]**:
| Status | CTA | Confirm? | Endpoint | 409 when |
|---|---|---|---|---|
`DRAFT` | `Publish posting` | no | `POST /jobs/{id}/open` | not DRAFT |
`OPEN` | `Close posting` | yes — “Stop accepting applications?” | `POST /jobs/{id}/close` | not OPEN |
`CLOSED` | `Reopen posting` | no | `POST /jobs/{id}/reopen` | not CLOSED |

**Transitions are strict, not idempotent** — a repeated call returns `409` **[V]**. On `409`: toast “This posting has already changed status.”, refetch, and re-render the CTA. Never retry automatically.

---

### S-24 · Edit job posting — `/recruiter/jobs/:jobId/edit`

`PUT /jobs/{jobId}` `{ companyName, role, location }` → `200` **[V]**.
Same form and validation as S-22, prefilled. **Status is not editable here** — `JobUpdateRequest` has no status field and the service ignores ownership changes **[V]**. Render a muted read-only `Status` row with the note “Change status from the posting page.”
`403` (“This job belongs to another organization”) → inline alert + `[Back to postings]`, not a hard redirect **[UX]**. `404` → S-08.
Dirty-form guard: navigating away with unsaved changes prompts “Discard changes?” **[UX]**.

---

### S-25 · Application lookup — `/recruiter/applications` **[GAP-01]**

The recruiter counterpart of S-11. No list endpoint exists, so this is an explicit, honest utility rather than a fake table.
```
H1 "Look up an application"
GapNotice "The API does not yet support listing applications. Enter an application ID
           to review it. Applications you have shortlisted appear under Recruitments."
[ Application ID (UUID) ______________________ ] [Open]
──────────────────────────────────────────────────────────
"Recently viewed"  ← client-side, last 10, from local storage
  Role & company | Status | Viewed | →
[Go to Recruitments →]
```
Validate the UUID shape client-side before calling. `GET /applications/{id}`: `404` → “No application with that ID is visible to you.” (correct wording — 404 here may mean *not yours*, and must not reveal which **[V]**). `403` → `/403`.

**When [GAP-01] is closed** (`GET /api/v1/applications?jobId=&status=&page=&size=`), this screen becomes `/recruiter/jobs/:jobId/applications` — a filterable table with the same row shape as "Recently viewed". Build the row component so it is reusable.

---

### S-26 · Application review — `/recruiter/applications/:applicationId`

**Purpose** The recruiter's main working screen: read the application, advance its status, shortlist it.
**Access** `RECRUITER` of the org owning the job (others → 404) **[V]**; `ADMIN` read-across **[V]**.
**Data** `GET /applications/{id}` **[V]** + `GET /jobs/{jobId}` for context **[V]**.

**Layout**
```
Breadcrumb  Applications / {applicationId short}
H1 "Application for {role}"   StatusBadge({status})
Meta  {companyName} · {location}
─────────────────────────────────────────────────────────────────────
LEFT (8)
  "Pipeline" card
     ApplicationStatusTimeline  (current position marked)
     APPLIED → UNDER_REVIEW → SHORTLISTED → HIRED
                                          ↘ REJECTED
                           ↘ WITHDRAWN (terminal off-ramp)
     Action row: ONLY the legal next transition(s) as buttons
  "Candidate" card
     Candidate ID  CopyableId({candidateId})
     GapNotice: "Candidate names and contact details are not exposed by the API."  [GAP-15]
  "Résumé" card
     resumeLink rendered as an external link ↗
     Warning: "This link was supplied by the candidate. If it points at a SmartHire
               document, you will not be able to open it — recruiters cannot read
               candidate documents in this release."                       [GAP-02]
RIGHT (4)
  "Recruitment" card
     no recruitment  → [Shortlist into recruitment]    (S-28)
     exists          → interview + decision badges, [Open recruitment] → S-30
  "Identifiers" DefinitionList: Application ID · Job ID · Candidate ID (all CopyableId)
```

**Status actions — derived strictly from `ApplicationService.updateStatus` + the aggregate [V]**
| Current | Buttons rendered | Target | Endpoint |
|---|---|---|---|
`APPLIED` | `Start review` | `UNDER_REVIEW` | `PATCH /applications/{id}/status {"status":"UNDER_REVIEW"}` |
`UNDER_REVIEW` | `Shortlist` | `SHORTLISTED` | `… {"status":"SHORTLISTED"}` |
`SHORTLISTED` | `Mark hired` · `Reject` | `HIRED` / `REJECTED` | `… {"status":"HIRED"\|"REJECTED"}` |
`HIRED` · `REJECTED` · `WITHDRAWN` | none — terminal | — | — |

Recruiters can **never** set `APPLIED` or `WITHDRAWN` — the service rejects both with `409` **[V]**. Do not render those as options.

> **The most important sequencing rule in the product.** `PATCH /recruitments/{id}/final-status` propagates the decision to the application via `synchronizeApplicationStatus`, and `Application.hire()`/`reject()` require the application to be **`SHORTLISTED`** **[V]**. So a recruitment decision will fail with `409` unless the application has already been walked `APPLIED → UNDER_REVIEW → SHORTLISTED`. The UI must make this ordering obvious — see S-30 and §7.4. (The Postman Review-1 collection omits these two steps; see **[GAP-06]**.)

**Notification side effect [V]** Transitions to `UNDER_REVIEW`, `SHORTLISTED`, `HIRED`, `REJECTED` trigger a best-effort candidate notification; delivery failure does **not** roll back the status change. Confirmation copy should therefore say “The candidate will be notified.” and never promise delivery.

**Errors [V]** `409` → §8.7 illegal-transition panel using `currentStatus` + `attemptedAction` from the problem body. `403` → inline “Your organization does not own the job for this application.” `404` → S-08. `503` → “Couldn't verify the job for this application.” + `[Retry]`.

---

### S-27 · Confirm status transition (dialog) **[V]**

One dialog, parameterised.
```
H2 "{action} this application?"
Body: "The application moves from {current} to {target}. The candidate will be notified."
      For Reject / Mark hired, add: "This is final and cannot be undone."   [V — terminal]
[Cancel]                                    [{action}]   ← danger style for Reject
```
Submit disabled while in flight, spinner in the button. On success: close, toast, re-render from the returned `ApplicationResponse`.

---

### S-28 · Shortlist into recruitment (dialog) **[V]**

`POST /recruitments { applicationId }` → `201` + `RecruitmentResponse` **[V]**.
```
H2 "Start a recruitment process"
Body: "This creates an interview and decision record for this application.
       Interview status starts at NOT_SCHEDULED and the decision at PENDING."
Callout (warning) shown when application.status !== 'SHORTLISTED':
  "This application is {status}. You can start the process now, but you must move the
   application to SHORTLISTED before you can record a final decision."       [V]
[Cancel]                                   [Start process]
```
**Errors [V]** `409` (“already been shortlisted”) → “A recruitment process already exists for this application.” + `[Open it]` (resolve the id by scanning `GET /recruitments`). `404` → “This application is not visible to your organization.” `403` → token carries no `orgId`: “Your account is not linked to an organization.” `503` → retry.
**Success** Toast “Recruitment process started.” → `/recruiter/recruitments/{recruitmentId}`.

---

### S-29 · Recruitments — `/recruiter/recruitments`

**Access** `RECRUITER`; server scopes to `ownerOrgId` from the token **[V]**.
**Data** `GET /recruitments?page&size` → `PagedModel<RecruitmentResponse>` **[V]**.

```
H1 "Recruitments"   subtitle "{totalElements} processes"
Filter chips (CLIENT-SIDE — endpoint accepts only page & size)  [V]
  Interview: All · Not scheduled · Scheduled · Completed · No show
  Decision:  All · Pending · Hired · Rejected
RecruitmentTable (card list ≤767)
  Role & company | Candidate | Interview | Decision | Actions | →
    Candidate = CopyableId(candidateId) truncated to 8 chars + copy    [GAP-15]
    Actions   = the one legal interview transition, inline
Pagination (server-side)
```
Label the chip group “Filtered on this page” with the same honesty note as S-21, since filtering is post-paging **[GAP-14]**.
`Role & company` requires `GET /jobs/{jobId}` per row — batch these and cache by `jobId` for the page **[UX]**.
**Empty** “No recruitments yet” + “Shortlist an application to start a process.”

---

### S-30 · Recruitment workflow detail — `/recruiter/recruitments/:recruitmentId`

**Purpose** Drive the interview lifecycle and record the decision. The most state-dense screen in the product.
**Access** Owning-org `RECRUITER` (others → 404) **[V]**.
**Data** `GET /recruitments/{id}` **[V]**; plus `GET /jobs/{jobId}` and `GET /applications/{applicationId}` for context and for the ordering guard below **[V]**.

**Layout**
```
Breadcrumb  Recruitments / {role}
H1 {role}      StatusBadge(interviewStatus)   StatusBadge(finalStatus)
Meta  {companyName} · {location} · Candidate CopyableId
─────────────────────────────────────────────────────────────────────────
LEFT (8)
  "Interview" card
     Timeline  NOT_SCHEDULED → SCHEDULED → COMPLETED ↘ NO_SHOW
     CTA row: only the legal transition(s)
     Muted note: "The platform records interview status only — no date, time or
                  interviewer."                                           [GAP-16]
  "Decision" card
     Timeline  PENDING → HIRED ↘ REJECTED
     finalStatus === PENDING  → [Record decision] (S-31)
     decided                  → locked summary + "Decisions are final."    [V]
     ⚠ PreconditionBanner when application.status !== 'SHORTLISTED':
        "Move the application to SHORTLISTED before recording a decision —
         the decision also updates the application, and that step will be
         rejected otherwise."                [ Open application → S-26 ]    [V]
        → disable [Record decision] while this holds
RIGHT (4)
  "Linked records" — Application (→ S-26) · Job (→ S-23), both CopyableId
  "Identifiers"    — Recruitment ID (CopyableId)
```

**Interview CTAs — exactly the aggregate's legal moves [V]**
| Current | CTA(s) | Body | 409 when |
|---|---|---|---|
`NOT_SCHEDULED` | `Mark interview scheduled` | `{"status":"SCHEDULED"}` | not NOT_SCHEDULED |
`SCHEDULED` | `Mark completed` · `Mark no-show` | `{"status":"COMPLETED"}` / `{"status":"NO_SHOW"}` | not SCHEDULED |
`COMPLETED` · `NO_SHOW` | none — terminal | — | — |

`PATCH /recruitments/{id}/interview-status` **[V]**. Interview and decision lifecycles are independent: a decision may be recorded at any interview status **[V]** — do not gate the decision on the interview.

**The precondition banner is the key design decision on this page.** It converts the hidden cross-service coupling (**[GAP-06]**) into a visible, actionable instruction, and it is derived from real data (`GET /applications/{id}.status`), not guessed.

---

### S-31 · Record final decision (dialog) **[V]**

`PATCH /recruitments/{id}/final-status { "status": "HIRED" | "REJECTED" }` **[V]**.
```
H2 "Record the final decision"
RadioGroup   ( ) Hired     ( ) Rejected
Callout (info): "This also updates the application to {HIRED|REJECTED} and notifies
                 the candidate. Decisions cannot be changed."
[Cancel]                                        [Record decision]
```
`PENDING` is not selectable — the service rejects a return to `PENDING` with `409` **[V]**.

**Errors [V]**
| Status | Server cause | UI |
|---|---|---|
`409` + `currentFinalStatus` | already decided | “A decision has already been recorded for this process.” + refetch |
`409` “cannot be moved to …” | `ApplicationSyncConflictException` — application was not `SHORTLISTED` | “The decision was **not** applied. Move the application to SHORTLISTED first.” + `[Open application]`. Emphasise *not applied*: the recruitment save is rolled back with the sync failure **[V]** |
`503` | application-service unreachable / circuit open | “The decision was not applied because the application service is unavailable. Nothing has changed.” + `[Retry]` |
`404` | not visible to this org | S-08 |

The "nothing has changed" wording is factually correct: `updateFinalStatus` is `@Transactional` and a sync failure rolls the decision back **[V]**. Getting this copy right prevents a recruiter from believing a decision landed when it did not.

---

### S-33 · Admin overview — `/admin/overview`

**Access** `ADMIN` **[V]**. The role's API surface is genuinely thin — design to that, not around it.
**Data** `GET /auth/admin/whoami` → `MeResponse` **[V]**; `GET /recruitments?size=1` for a total count **[V]**.

```
H1 "Platform overview"
Card "Your access"     — email, roles, user ID, "ADMIN — platform operations"
Card "Recruitments"    — {totalElements} total   [View all →]
GapNotice "Administrator tooling is limited in this release. There are no APIs for
           managing users, organizations or job postings. Recruiter and administrator
           accounts can self-register, but there is no user-administration surface." [GAP-03]
Card "Operations"      — [API documentation ↗]  [Service registry ↗ :8761]
```
`GET /auth/admin/whoami` doubles as the ADMIN authorization probe: a `403` here means the token lacks the role → route to `/403` **[V]**.
No counts for jobs, applications, users, or documents — none of those are countable by an admin without a list endpoint. **Do not render zero or placeholder KPI tiles.**

---

### S-34 · All recruitments — `/admin/recruitments`

Identical component to S-29, admin scope (server returns **all** recruitments for ADMIN) **[V]**. Subtitle “{totalElements} across all organizations.” No write actions — ADMIN cannot drive interview or final status **[V]**; the Actions column is omitted entirely rather than disabled.

---

### S-35 · Record lookup — `/admin/lookup`

Admin read-across utility, grounded in the three by-id endpoints ADMIN may call **[V]**.
```
H1 "Record lookup"
SegmentedControl  Application · Document · Recruitment
[ ID (UUID) ______________________ ]  [Look up]
Result panel (per type)
  Application  → full ApplicationResponse DefinitionList + [Open job]
  Document     → DocumentResponse DefinitionList + [Download]   ← ADMIN may read content [V]
  Recruitment  → RecruitmentResponse + both timelines
GapNotice "Lookup is by ID. Listing and searching records is not available to
           administrators in this release."                               [GAP-03]
```
ADMIN is the one role that can read any document's metadata **and** content **[V]** — this is the only place that capability is reachable, so expose it here.

---

### S-37 / S-38 / S-39 · Planned AI screens — generate locked

See §11. Generate the shells so the information architecture is complete, with every control disabled and a `PlannedBanner`. **No mock scores, no sample rationales, no fabricated parsed fields.**

---

## 6. Component specifications

### 6.1 Button

| Variant | Use | Resting | Hover | Active | Focus | Disabled |
|---|---|---|---|---|---|---|
`primary` | one per view | `--c-primary-600` bg, white text | `--c-primary-700` | `--c-primary-800` | 2px `--c-focus` ring, 2px offset | `--c-primary-600` @ 38 %, `cursor:not-allowed` |
`secondary` | alternates | `--c-surface` bg, `--c-border-strong` border, `--c-text` | `--c-surface-sunken` bg | `--c-border-strong` bg | same | 38 % opacity |
`ghost` | tertiary/table rows | transparent | `--c-surface-sunken` | `--c-border` | same | 38 % |
`danger` | destructive confirm | `--c-danger-600` bg, white | `--c-danger-700` | `--c-danger-800` | same | 38 % |
`link` | inline navigation | transparent, `--c-primary-600`, underline on hover | — | — | same | 38 % |

Sizes — `sm` h32 / px12 / 13px · `md` h40 / px16 / 14px · `lg` h48 / px20 / 16px. Radius `--r-md` (6 px). Icon-only buttons are square at the same height and **require** an `aria-label`.
**Loading** replaces the leading icon with a 16 px spinner, keeps the label, sets `aria-busy="true"`, and disables the control. Width must not change (reserve label width) — no layout jump.

### 6.2 Form controls

- **Input / Textarea / Select** — h40 (`md`), 12 px horizontal padding, 1 px `--c-border`, radius `--r-md`, `--c-surface` background. Focus: `--c-primary-500` border + 3 px `--c-focus-ring`. Error: `--c-danger-500` border + 3 px danger ring.
- **Label** 13/600, 6 px above. Required marked with `*` in `--c-danger-600` **and** `aria-required`.
- **Helper text** 12/400 `--c-text-muted`, 4 px below.
- **Error text** 12/500 `--c-danger-700`, 4 px below, with a 14 px alert icon; `role="alert"`; replaces helper text; `aria-describedby` wired to the input; `aria-invalid="true"`.
- **Character counter** 12/400 muted, right-aligned, appears at ≥80 % of max; turns `--c-danger-700` at 100 %.
- **Disabled** `--c-surface-sunken` background, `--c-text-subtle` text, border unchanged.
- **Validation timing [UX]** — validate on blur, re-validate on change **only after** the first blur, and validate everything on submit. Never validate on first keystroke.

### 6.3 StatusBadge

Pill, h22, px8, radius `--r-full`, 12/600, uppercase tracking 0.02em, 6 px dot on the left. One token pair per enum value (§7.2). **Every status in the system renders through this one component** — never ad-hoc colour.

### 6.4 DataTable

```
Header   h44, --c-surface-sunken bg, 12/600 uppercase --c-text-muted, 1px bottom border
Row      h56, 1px --c-border bottom; hover --c-surface-sunken; focus-visible 2px inset ring
Cell     px16, 14/400, vertical-align middle; numerics & IDs tabular-nums
Actions  right-aligned, ghost buttons, revealed on row hover/focus but ALWAYS present
         for touch and keyboard (opacity, never display:none)
Row link whole row navigates; actions stopPropagation
```
- **No sortable headers.** Server sort is fixed (`jobId`, `recruitmentId`) and there is no `sort` parameter **[V]**.
- **Column priority** drives responsive dropping (§6.6).
- **≤767 px** the table is replaced by a **card list**: primary identifier as the heading, StatusBadge top-right, two or three secondary fields as label/value pairs, actions in a footer row.
- Loading = 5 skeleton rows at real row height (no collapse). Empty = `EmptyState` spanning all columns.

### 6.5 Pagination

```
[« First] [‹ Prev]   Page {n} of {total}   [Next ›] [Last »]      Rows: [20 ▾]
Left-aligned summary: "Showing {from}–{to} of {totalElements}"
```
Derive from the response envelope — three shapes exist (§9.9). Size options **10 · 20 · 50 · 100**; **100 is the server-enforced maximum** and must be the largest offered **[V]**. Disable `Prev`/`First` on page 0 and `Next`/`Last` on the last page. At ≤640 px, collapse to `[‹] Page n of m [›]`. Page and size belong in the URL query string.

### 6.6 Column priority

| Table | P1 (always) | P2 (drop <1024) | P3 (drop <1280) |
|---|---|---|---|
Jobs (recruiter) | Role, Status, Actions | Company | Location |
Recruitments | Role, Interview, Decision | Candidate | Actions |
Documents | File name, Actions | Type | Size, Uploaded |
Applications (S-11) | Role & company, Status | Location | Source, Last checked |

### 6.7 StatusTimeline

Horizontal at ≥768 px, vertical below.
```
 ●━━━━━━━━●━━━━━━━━○┄┄┄┄┄┄┄┄○
 Applied  Under     Short-    Hired
          review    listed
```
- **Complete** filled `--c-primary-600` node, solid `--c-primary-300` connector.
- **Current** filled node in the status's own colour, 2 px `--c-surface` ring + 2 px status-colour outer ring, label 13/600.
- **Future** hollow node `--c-border-strong`, dashed connector, label `--c-text-subtle`.
- **Branch** (`HIRED`/`REJECTED`, `COMPLETED`/`NO_SHOW`) — fork into two short stubs; the untaken branch stays hollow and dimmed.
- **Terminal off-ramp** (`WITHDRAWN`) — rendered only once reached, as a detached node below with a dashed connector from the status it left.
`aria-label` on the container: “Application status: {current} — step {n} of {total}”.

### 6.8 Dialog / ConfirmDialog

Centred modal, max-w 520 px (`sm`) / 640 px (`md`), radius `--r-lg` (12 px), `--shadow-lg`, 24 px padding. Scrim `rgba(28,26,25,0.48)`.
Behaviour: focus moves to the dialog on open and is **trapped**; `Esc` and scrim click cancel (suppressed while a request is in flight); focus returns to the trigger on close; `role="dialog" aria-modal="true"`, title wired via `aria-labelledby`; body scroll locked.
`ConfirmDialog` adds a destructive mode: danger confirm button, confirm button placed **right**, cancel **left**, and `Cancel` carries initial focus for destructive actions **[UX]**.

### 6.9 Toast

Bottom-right, 16 px inset, max 3 stacked, 360 px wide. `success` / `error` / `info` / `warning` with a leading icon and a 2 px left accent bar. Auto-dismiss: success/info 4 s, warning 6 s, **error never auto-dismisses**. Dismiss `×` always present. `role="status"` (`role="alert"` for errors). Pause auto-dismiss on hover/focus.

### 6.10 EmptyState

Centred, 64 px vertical padding: 48 px muted line icon → 16/600 title → 14/400 muted body (max 44ch) → optional primary action. Every list screen in §5 specifies its own title and body copy; never a generic “No data”.

### 6.11 ErrorPanel

Bordered panel, `--c-danger-50` background, `--c-danger-200` border, radius `--r-md`, 16 px padding: 20 px alert icon · 14/600 title · 13/400 body · optional `[Retry]` (secondary) · and, when present, the `correlationId` as a monospace `CopyableId` labelled “Reference”. Surfacing the correlation id is a real capability — every service response carries `X-Correlation-Id` and problem bodies include `correlationId` **[V]** — and it is what makes a support conversation possible.

### 6.12 GapNotice

The honesty primitive. Inline panel, `--c-surface-sunken` background, dashed 1 px `--c-border-strong` left border 3 px solid `--c-text-subtle`, radius `--r-sm`, 12 px padding: 16 px info icon · 13/500 title · 13/400 muted body · optional action.
**Used wherever a user would reasonably expect a capability that the backend does not provide.** Never replaced by invented data, placeholder rows, or `0` counts.

### 6.13 PlannedBanner

For §11 screens only. `--c-accent-50` background, `--c-accent-500` 1 px border, a `PLANNED` pill, and copy naming the dependency: “AI features require the SmartHire AI service, which is not deployed in this release.” All controls inside the screen are `disabled` with `aria-disabled="true"`.

### 6.14 CopyableId

Monospace 13/400, `--c-surface-sunken` background, 2 px/6 px padding, radius `--r-sm`, with a 14 px copy icon button. Truncates to the first 8 characters plus `…` when `compact`; the full value is in `title` and copies in full. On copy: swap to a check icon for 1.5 s and announce “Copied” via a live region. Used for every UUID in the product — of which there are many, because the API exposes identity only as UUIDs **[GAP-15]**.

### 6.15 FileDropzone

Dashed 2 px `--c-border-strong`, radius `--r-md`, 32 px padding, centred. Hover/drag-over: `--c-primary-500` border + `--c-primary-50` background. Error: danger border + background. Contains a 32 px upload icon, 14/500 “Drag a file here or browse”, 12/400 muted constraint line “PDF, DOC or DOCX · up to 10 MB”, and a visually hidden `<input type="file">` reachable by keyboard with a visible focus ring on the zone. Rejected drops produce an inline error and **do not** clear a previously valid selection.

### 6.16 Other components

- **RoleChip** — `Candidate` `--c-primary-50`/`700` · `Recruiter` `--c-accent-50`/`700` · `Admin` `--c-warning-50`/`800`. 12/600, h20, radius full.
- **FilterBar** — `--c-surface` row, 1 px border, radius `--r-md`, 12 px padding, 8 px gaps, wraps at `sm`. `[Clear]` is a ghost button, shown only when a filter is active, and shows the active-filter count.
- **Breadcrumb** — 13/400 muted, `/` separators, last crumb `--c-text` 13/500 and not a link. Collapses to `← {parent}` at ≤640 px.
- **Skeleton** — `--c-surface-sunken`, radius `--r-sm`, 1.6 s shimmer; suppressed under `prefers-reduced-motion`.
- **DefinitionList** — two columns at ≥640 px (label 160 px, `--c-text-muted` 13/500; value `--c-text` 14/400), stacked below; 12 px row gap; `—` for null values, never a blank cell.
- **Tabs / SegmentedControl** — h36, `--c-surface-sunken` track, active item `--c-surface` with `--shadow-sm`; `role="tablist"`, arrow-key navigation.
- **Banner** — full-width page-level alert above the page header; `info` / `warning` / `danger` / `success`; optional dismiss; used for session, rate-limit, and partial-failure messaging.

---

## 7. Design system

### 7.1 Visual direction

**Calm institutional.** A hiring decision is consequential and the data is dense; the interface should read as a well-made instrument, not a consumer marketing site.

Four commitments:
1. **Warm neutrals, cool accent.** Surfaces carry a slight warmth (`#FBFAF9`, `#F4F2F0`) against a deep indigo primary. This avoids the grey-on-grey flatness of generic admin templates while keeping long reading sessions comfortable.
2. **Status is the loudest thing on screen.** Colour is spent almost entirely on state. Chrome is quiet so that `SHORTLISTED` vs `REJECTED` is legible at a glance.
3. **Borders over shadows.** Structure comes from 1 px hairlines; elevation is reserved for genuinely floating layers (dialog, dropdown, toast). Flat, dense, scannable.
4. **No decorative imagery.** Illustration appears only in empty states, as a single muted line icon. There is no hero art, no stock photography, no gradient.

### 7.2 Color tokens

**Neutral (warm)**
```
--c-bg              #FBFAF9    page background
--c-surface         #FFFFFF    cards, inputs, table rows
--c-surface-sunken  #F4F2F0    table headers, tracks, gap notices
--c-border          #E3DFDB    hairlines, input borders
--c-border-strong   #CBC5BF    emphasised borders, hollow nodes
--c-text            #1C1A19    primary text
--c-text-muted      #6B6560    labels, secondary text
--c-text-subtle     #928C86    disabled, placeholder, future states
--c-overlay         rgba(28,26,25,0.48)
```

**Primary (ink indigo)**
```
--c-primary-50   #EEF0F8      --c-primary-600  #3E4A91   default button
--c-primary-100  #DCE0F0      --c-primary-700  #313A75   hover
--c-primary-300  #9BA4D4      --c-primary-800  #252C5A   active
--c-primary-500  #4F5BA6      --c-primary-900  #1A1F40   sidebar
```

**Accent (teal — AI / planned surfaces only)**
```
--c-accent-50  #E6F2F2    --c-accent-500 #0E7C7B    --c-accent-700 #0A5F5E
```

**Semantic**
```
success  50 #E7F4EC  200 #B7DEC6  600 #1A6B3C  700 #145430
warning  50 #FDF3E0  200 #F2DCA8  600 #8A5A00  800 #5E3D00
danger   50 #FBEAEA  200 #F0C2C2  500 #C53030  600 #A32424  700 #841C1C  800 #6B1616
info     50 #E8F0FA  200 #BBD3EF  600 #1E5EA8  700 #174A85
--c-focus       #4F5BA6
--c-focus-ring  rgba(79,91,166,0.32)
```

**Status mapping — the complete set of backend enum values [V].** No other status strings exist in the system.

| Enum value | Owner | Text | Background | Dot |
|---|---|---|---|---|
`DRAFT` | Job | `--c-text-muted` | `--c-surface-sunken` | `#928C86` |
`OPEN` | Job | `success-600` | `success-50` | `#1A6B3C` |
`CLOSED` | Job | `--c-text` | `#E8E5E2` | `#6B6560` |
`APPLIED` | Application | `info-600` | `info-50` | `#1E5EA8` |
`UNDER_REVIEW` | Application | `warning-600` | `warning-50` | `#8A5A00` |
`SHORTLISTED` | Application | `--c-primary-700` | `--c-primary-50` | `#3E4A91` |
`HIRED` | Application · Recruitment | `success-700` | `success-50` | `#145430` |
`REJECTED` | Application · Recruitment | `danger-700` | `danger-50` | `#A32424` |
`WITHDRAWN` | Application | `--c-text-muted` | `--c-surface-sunken` | `#928C86` |
`NOT_SCHEDULED` | Recruitment | `--c-text-muted` | `--c-surface-sunken` | `#928C86` |
`SCHEDULED` | Recruitment | `warning-600` | `warning-50` | `#8A5A00` |
`COMPLETED` | Recruitment | `info-600` | `info-50` | `#1E5EA8` |
`NO_SHOW` | Recruitment | `danger-600` | `danger-50` | `#C53030` |
`PENDING` | Recruitment | `--c-text-muted` | `--c-surface-sunken` | `#928C86` |
`SENT` | Notification | `success-600` | `success-50` | `#1A6B3C` |
`FAILED` | Notification | `danger-600` | `danger-50` | `#C53030` |

`PENDING` appears for both notification delivery and recruitment decision — same token pair, context disambiguates.
**Colour is never the only carrier**: every badge pairs colour with its text label, and the dot is redundant reinforcement, so the palette is safe for colour-vision deficiency.

### 7.3 Typography

```
--font-sans  "Inter", -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif
--font-mono  "JetBrains Mono", ui-monospace, "SF Mono", Menlo, Consolas, monospace
```
`--font-mono` is used for **UUIDs, correlation ids, and file sizes** — identifiers the user copies, where character disambiguation matters.

| Token | Size / line | Weight | Use |
|---|---|---|---|
`display` | 30 / 36 | 600 | auth screen H1 |
`h1` | 24 / 32 | 600 | page title |
`h2` | 20 / 28 | 600 | dialog title, section |
`h3` | 18 / 26 | 600 | card title |
`body-lg` | 16 / 24 | 400 | long-form body |
`body` | 14 / 20 | 400 | default UI, table cells |
`body-strong` | 14 / 20 | 500 | emphasis, active crumb |
`label` | 13 / 18 | 600 | form labels |
`caption` | 12 / 16 | 400 | helper, counters |
`overline` | 12 / 16 | 600 | table headers, badges — uppercase, 0.02em |

Letter-spacing `-0.01em` at ≥20 px, `0` at 14–18 px, `+0.01em` at ≤13 px. Measure capped at **72 ch** for prose. Font smoothing: antialiased.

### 7.4 Spacing, sizing, radius, elevation

```
Spacing (4px base)   --s-0 0 · --s-1 4 · --s-2 8 · --s-3 12 · --s-4 16 · --s-5 20
                     --s-6 24 · --s-8 32 · --s-10 40 · --s-12 48 · --s-16 64
Control heights      --h-sm 32 · --h-md 40 · --h-lg 48
Row heights          table header 44 · table row 56 · list row 72 · nav item 40
Fixed widths         sidebar 240 / rail 64 · top bar 56 · auth card 400
                     content max 1200 · public content max 1120 · prose max 72ch
Radius               --r-sm 4 · --r-md 6 · --r-lg 12 · --r-full 999
Elevation            --shadow-sm  0 1px 2px rgba(28,26,25,.06)
                     --shadow-md  0 4px 12px rgba(28,26,25,.08)
                     --shadow-lg  0 16px 40px rgba(28,26,25,.16)
Z-index              base 0 · sticky 10 · dropdown 20 · drawer 30
                     scrim 40 · dialog 50 · toast 60
Motion               --t-fast 120ms · --t-base 180ms · --t-slow 240ms
                     easing cubic-bezier(.2,0,.2,1)
```
Animate `opacity` and `transform` only. Under `prefers-reduced-motion: reduce`, durations drop to 0 ms and shimmer is replaced by a static tint.

### 7.5 Interaction states — required for every interactive element

| State | Treatment |
|---|---|
Default | per component |
Hover | background shifts one step; **never** a size or font change |
Focus-visible | 2 px `--c-focus` ring, 2 px offset, on **every** focusable element — never removed |
Active | background shifts two steps |
Disabled | 38 % opacity, `cursor:not-allowed`, `aria-disabled="true"`, tooltip giving the reason |
Loading | spinner replaces leading icon, label retained, `aria-busy="true"`, width preserved |
Error | danger border + 3 px danger ring + `role="alert"` message + `aria-invalid` |
Selected | `--c-primary-50` background, `--c-primary-600` 2 px left/bottom marker |
Read-only | `--c-surface-sunken` background, normal text, no border change |

### 7.6 Accessibility rules (WCAG 2.2 AA target)

1. **Contrast** — body text ≥ 4.5:1, ≥18 px/bold ≥ 3:1, UI borders and focus indicators ≥ 3:1. Every pair in §7.2 is chosen to clear this against its paired background.
2. **Focus visible** — never suppress the ring. `:focus-visible` for pointer users, always-on for keyboard.
3. **Keyboard reachability** — every action reachable and operable by keyboard, including table row actions (they must not be `display:none` when unhovered) and the file input inside `FileDropzone`.
4. **Focus order and trapping** — DOM order matches visual order; dialogs and drawers trap focus and restore it to the trigger.
5. **Semantics** — one `<h1>` per page, no skipped heading levels; `<table>` with `<th scope="col">`; `<nav>`/`<main>`/`<aside>` landmarks; a `Skip to content` link as the first focusable element.
6. **Labels** — every control has a programmatic label. Placeholders are never the label. Icon-only buttons carry `aria-label`.
7. **Error identification** — errors are text (not colour alone), tied via `aria-describedby`, `aria-invalid` set, and focus moves to the first invalid field on failed submit.
8. **Live regions** — toasts `role="status"` (errors `role="alert"`); async list updates announce “{n} results”; copy actions announce “Copied”.
9. **Status is never colour-only** — badge text label is mandatory.
10. **Target size** — interactive targets ≥ 24×24 px (WCAG 2.2 §2.5.8); ≥ 44×44 px on touch breakpoints.
11. **Motion** — honour `prefers-reduced-motion`; no auto-playing or looping motion beyond the 1.6 s skeleton shimmer.
12. **Zoom and reflow** — usable at 200 % zoom and at a 320 px CSS viewport width without two-dimensional scrolling.
13. **Language and direction** — `lang="en"`; no hard-coded LTR assumptions in layout (use logical properties).

---

## 8. Session, authorization, and error states

### 8.1 Session model [V]

| Fact | Value | Source |
|---|---|---|
Access token | RS256 JWT, **15 min** (`PT15M`) | `smarthire.jwt.access-token-ttl` |
Claims | `sub`, `roles[]`, `orgId?`, `iss`, `aud`, `exp`, `iat`, `jti` | `JwtIssuer` |
Issuer / audience | `https://smarthire.local/auth` / `smarthire` | `application.yml` |
Storage | **in memory only** | ADR-0014, architecture §6 |
Refresh token | opaque, **7 days** (`P7D`), rotated on use | `RefreshTokenService` |
Refresh transport | cookie `smarthire_refresh`; `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` | `AuthController` |
Transmission | `Authorization: Bearer <token>` on every non-public call | gateway + all services |
Revocation | `jti` denylist checked at the gateway on every request | `JwtRevocationWebFilter` |
CORS | origin `http://localhost:5173`, credentials allowed, `maxAge` 3600 | gateway `globalcors` |

The frontend dev server must run on the configured origin (default `http://localhost:5173`) or `GATEWAY_CORS_ALLOWED_ORIGINS` must be updated. `allowCredentials: true` plus a concrete origin is required for the refresh cookie — a wildcard origin would break it **[V]**.

### 8.2 Unauthenticated — 401

Triggered by: no token, expired token, bad signature, wrong `iss`/`aud`, or a revoked `jti` **[V]**. Response is `application/problem+json` with title `Unauthorized` **[V]**.

**Handling** — one interceptor, three outcomes:
1. **Public route** → ignore; render the anonymous view.
2. **Protected route, refresh plausible** (not already retried) → attempt silent refresh (§8.6), replay the original request once on success.
3. **Refresh failed or unavailable** → clear in-memory auth, redirect to `/session-expired?next={path}`.

Never show a raw 401 to the user, and never retry a refresh more than once per failed request.

### 8.3 Forbidden — 403 → `/403`

Triggered by a valid token lacking the required role (`@PreAuthorize`), or an ownership failure that the service chose to report explicitly (e.g. `ApplicationForbiddenException`, `JobForbiddenException`) **[V]**.
```
Icon (lock, 48, muted)
H1 "You don't have access to this page"
Body "You're signed in as {email} with the {role} role. This page requires a
      different role."
[Go to my dashboard]   [Sign out]
```
Show the role so the refusal is explicable (G2). **Prevent 403s by construction**: role-aware navigation plus route guards derived from `roles[]` mean a user should rarely reach this page by normal navigation.

### 8.4 Not found / privacy-masked — 404 → `/404`

Several endpoints return 404 where 403 would be truthful, so that identifiers cannot be probed **[V]**: another org's `DRAFT`/`CLOSED` job, another candidate's application, another org's application or recruitment, another candidate's document, and **any** document requested by a recruiter.
```
H1 "We couldn't find that"
Body "It may have been removed, or you may not have access to it."
[Go back]   [Go to my dashboard]
```
The deliberately ambiguous second sentence is correct and must not be "improved" into a disclosure.

### 8.5 Loading states

| Context | Treatment |
|---|---|
Initial page | Full skeleton matching final layout (header + body) |
Table/list refetch | Keep existing rows at 60 % opacity + 2 px top progress bar — **no** flash to skeleton |
Button submit | In-button spinner, control disabled, width preserved |
File upload | Determinate progress bar + `{percent}%`; `[Cancel]` aborts the request |
Route transition | 2 px top progress bar after a 150 ms delay (avoids flicker on fast loads) |
Inline field async | 16 px spinner inside the field's right edge |

Skeletons must never cause layout shift when real content arrives.

### 8.6 Token expiry and silent refresh [V + UX]

```
                    token issued
                         │
        ┌────────────────┴────────────────┐
        │  schedule refresh at exp − 60s   │
        └────────────────┬────────────────┘
                         │
            POST /api/v1/auth/refresh   (cookie only, no body)
                         │
          ┌──────────────┴──────────────┐
       200│                             │401 / network / no cookie
          ▼                             ▼
  replace in-memory token      clear auth → /session-expired?next=…
  reschedule next refresh
```
- Refresh is also attempted reactively on the **first** 401 of any protected request, then the request is replayed once **[UX]**.
- **Concurrent 401s must share one refresh.** Queue in-flight requests behind a single refresh promise; replay all on success, fail all on failure. Without this, a dashboard firing five parallel calls will burn five refresh-token rotations and hit the 10 rps per-IP auth limit **[V]**.
- **`/session-expired` (S-06)** — “Your session has expired · For your security, sessions end after a period of inactivity.” + `[Sign in again]` (carries `next`) + `[Browse jobs]`. Use a dedicated interstitial rather than a bare redirect to `/login` so the user understands they were not logged out by choice.
- **Known constraint:** the `Secure` refresh cookie is not stored by browsers over plain `http://localhost`, so in a default local run refresh will fail and the session ends at 15 minutes **[V]**, **[GAP-08]**. The interstitial is the designed outcome, not a bug to hide.
- `POST /auth/logout` revokes the refresh token and denylists the access token's `jti` **[V]**. Call it, then clear local state **regardless of the response** — a failed logout must still clear the client.

### 8.7 Validation and conflict errors

**Problem Details shape — every service, every error [V]** (RFC 9457, ADR-0016):
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Only a SHORTLISTED application can be hired; this application is APPLIED.",
  "instance": "/api/v1/applications/{id}/status",
  "correlationId": "…"
}
```
Plus typed extras the UI should use rather than parsing `detail` **[V]**:
- Application illegal state: `currentStatus`, `attemptedAction`
- Recruitment illegal state: `currentInterviewStatus`, `currentFinalStatus`, `attemptedAction`

**400** — map to fields where possible; otherwise a form-level alert carrying `detail`. Focus the first invalid field.
**409 — the illegal-transition panel** (the system's signature error, and the one most worth designing well):
```
┌─────────────────────────────────────────────────────────┐
│ ⚠  This change is no longer possible                    │
│    This application is {currentStatus}, and             │
│    "{attemptedAction}" needs a different status.        │
│    Someone may have updated it since this page loaded.  │
│                                 [Refresh and try again] │
└─────────────────────────────────────────────────────────┘
```
`[Refresh and try again]` refetches the resource and re-renders available actions. **Never auto-retry a 409** — the state genuinely changed. Because the enums are known (§7.2), the panel can always name both the current and attempted state rather than echoing a raw string.

### 8.8 Rate limiting — 429 [V]

Gateway `RequestRateLimiter` (Redis token bucket), per route:

| Route | Limit | Key |
|---|---|---|
`/api/v1/auth/**` | 10 rps · 20 burst | client IP |
`/api/v1/applications/**` | 20 rps · 40 burst | user |
`/api/v1/recruitments/**` | 15 rps · 30 burst | user |
`/api/v1/documents/**` | 10 rps · 20 burst | user |
`/api/v1/jobs/**`, `/api/v1/notifications/**` | none | — |

Responses on the limited application/recruitment/document routes carry `X-RateLimit-Policy` **[V]**.
**UI** — warning `Banner`: “You're going a bit fast · Too many requests. Wait a moment and try again.” Disable the triggering control for 10 s with a visible countdown; honour `Retry-After` when present. On auth screens, treat it as a security measure and say so plainly (§S-04). Note the limiter **fails closed** (`FailClosedRedisRateLimiter`) **[V]** — a Redis outage produces 429s, not an open gate, so the copy must not promise that retrying will definitely work.

### 8.9 Network failure → `/offline` or inline [UX]

A request that never completes (DNS, connection refused, timeout) is not an HTTP status.
- **In a list/detail view** → inline `ErrorPanel`: “We couldn't reach SmartHire · Check your connection and try again.” + `[Retry]`. Keep already-loaded content visible.
- **On app boot with nothing rendered** → `/offline` full page, same copy + `[Retry]`.
- Retry with backoff `1s · 2s · 4s`, max 3 attempts, **only for idempotent `GET`s**. Never auto-retry `POST`/`PATCH`/`PUT`/`DELETE` — a replayed `POST /applications` creates a duplicate application, which nothing server-side prevents **[GAP-07]**.
- Show a persistent top `Banner` “You appear to be offline” while `navigator.onLine === false`; clear it on reconnect and refetch the active view.

### 8.10 Server error — 500 / 503

**500** (`GlobalExceptionHandler`, generic detail + `correlationId`) **[V]**
```
H1 "Something went wrong on our side"
Body "The problem has been logged. If you contact support, share this reference."
Reference: <CopyableId correlationId>
[Try again]   [Go to my dashboard]
```
Never surface a stack trace or internal detail — the backend deliberately returns neither **[V]**.

**503** — a dependency is down, and the services distinguish the cases **[V]**: `JobServiceUnavailableException` (application-service → job-service), `ApplicationServiceUnavailableException` (recruitment-service → application-service, including open circuit / full bulkhead / exhausted retries). Copy must name the blocked action and confirm that nothing changed:
- apply → “We couldn't verify this posting right now. Your application was not submitted.”
- status change → “We couldn't reach the application service. Nothing has changed.”
- final decision → “The decision was not applied. Nothing has changed.” (accurate: the transaction rolls back **[V]**)
Always offer `[Retry]`; never auto-retry a write.

### 8.11 Correlation id — use it everywhere [V]

Every response carries `X-Correlation-Id`, and every problem body includes `correlationId`. Capture it on failure and surface it in `ErrorPanel` / the 500 page as a copyable “Reference”. This is a genuine, already-implemented capability and it is the difference between “it broke” and a diagnosable report.

---

## 9. Backend integration appendix

**Base URL** `http://localhost:8080` — the API Gateway, the only public entry point **[V]**.
**Auth header** `Authorization: Bearer <accessToken>` on every endpoint except those marked *public*.
**Content type** `application/json` except document upload (`multipart/form-data`) and document download (binary).
**Error type** `application/problem+json` (RFC 9457) everywhere **[V]**.

Service ports are internal and listed for operator reference only; the frontend must never address them: registry 8761 · job 8081 · auth 8082 · application 8083 · recruitment 8084 · document 8085 · notification 8086 · ai 8087 **[V]**.

### 9.1 Authentication — `/api/v1/auth`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
POST | `/register` | *public* (ADMIN needs code) | `{email, password, role?, organizationName?, adminInviteCode?}` | `201 {userId}` | 400 validation · 403 admin code · 409 email exists |
POST | `/login` | *public* | `{email, password}` | `200 {tokenType,accessToken,accessTokenExpiresAt}` + `Set-Cookie` | 400 · 401 · 429 |
POST | `/refresh` | *public* (cookie) | — (cookie `smarthire_refresh`) | `200 TokenResponse` + rotated cookie | 401 invalid/expired/missing |
POST | `/logout` | authenticated | — | `204` | 401 |
GET | `/me` | authenticated | — | `200 {sub,email,roles[],orgId}` | 401 |
GET | `/admin/whoami` | **ADMIN** | — | `200 MeResponse` | 401 · 403 |
GET | `/oauth2/jwks` | *public* | — | `200` JWKS | — |

**Validation [V]** `register.email` `@NotBlank @Email @Size(max=255)` · `register.password` `@NotBlank @Size(min=12,max=128)` · `register.role` `@Pattern("(?i)CANDIDATE|RECRUITER|ADMIN")`, optional and defaulting to `CANDIDATE` · `register.organizationName` `@Size(max=255)`, required for `RECRUITER` by `AuthService` · `register.adminInviteCode` `@Size(max=256)`, validated for `ADMIN` by `RegistrationPolicy` · `login.email` `@NotBlank @Size(max=255)` · `login.password` `@NotBlank @Size(max=128)`.
`MeResponse` is read from the **database**, not the token, so it reflects live state **[V]**. `orgId` is `null` for candidates and administrators; a recruiter carries the organization created at registration (or the seeded organization).
The login role selector is not part of the login request. Clients must treat the returned `roles[]` as authoritative and route accordingly.
Note `/oauth2/jwks` is **not** under `/api/v1` **[V]**. The frontend has no reason to call it.

### 9.2 Jobs — `/api/v1/jobs`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
GET | `/` | *public* | query `status,location,q,mine,page,size` | `200 PagedModel<JobResponse>` | 400 · 403 for `mine` without recruiter role |
GET | `/{jobId}` | *public* for OPEN | — | `200 JobResponse` | 404 |
POST | `/` | **RECRUITER** | `{companyName,role,location}` | `201 JobResponse` (DRAFT) + `Location` | 400 · 401 · 403 |
PUT | `/{jobId}` | **RECRUITER** owner | `{companyName,role,location}` | `200 JobResponse` | 400 · 401 · 403 · 404 |
POST | `/{jobId}/open` | **RECRUITER** owner | — | `200 JobResponse` (OPEN) | 401 · 403 · 404 · 409 |
POST | `/{jobId}/close` | **RECRUITER** owner | — | `200 JobResponse` (CLOSED) | 401 · 403 · 404 · 409 |
POST | `/{jobId}/reopen` | **RECRUITER** owner | — | `200 JobResponse` (OPEN) | 401 · 403 · 404 · 409 |

`JobResponse` = `{ jobId:UUID, companyName:String, role:String, location:String, status:"DRAFT"|"OPEN"|"CLOSED", ownerOrgId:UUID }` **[V]**.
**Create/update validation** all three fields `@NotBlank @Size(max=255)` **[V]**.
**Query params** `status` is the enum (unknown value → 400 at conversion) · `location` and `q` are contains matches, `q` spanning company **and** role · `mine` is a boolean that can only narrow a recruiter's listing to their own organization · `page` floored at 0 · `size` defaults 20, **clamped to 100** · sort fixed to `jobId` **[V]**.
**Visibility** anonymous/candidate/admin see `OPEN` only; `RECRUITER` additionally sees own-org `DRAFT`/`CLOSED` **[V]**. `mine=true` restricts a recruiter to their own organization and returns `403` for any other role. There is no `ownerOrgId` parameter, by design **[V]**.
**Transitions** `DRAFT→OPEN` · `OPEN→CLOSED` · `CLOSED→OPEN`. Strict and non-idempotent — repeating one returns `409` **[V]**.

### 9.3 Applications — `/api/v1/applications`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
POST | `/` | **CANDIDATE** | `{jobId, resumeLink}` | `201 ApplicationResponse` (APPLIED) + `Location` | 400 · 401 · 403 · 404 job · 409 job not OPEN · 429 · 503 |
GET | `/{applicationId}` | authenticated (scoped) | — | `200 ApplicationResponse` | 401 · 404 |
POST | `/{applicationId}/withdraw` | **CANDIDATE** owner | — | `200 ApplicationResponse` (WITHDRAWN) | 401 · 403 · 404 · 409 |
PATCH | `/{applicationId}/status` | **RECRUITER** owner-org · **ADMIN** | `{status}` | `200 ApplicationResponse` | 400 · 401 · 403 · 404 · 409 · 503 |
~~GET~~ | ~~`/`~~ | — | — | — | **DOES NOT EXIST — [GAP-01]** |

`ApplicationResponse` = `{ applicationId, jobId, candidateId, resumeLink, status }`, status ∈ `APPLIED·UNDER_REVIEW·SHORTLISTED·HIRED·REJECTED·WITHDRAWN` **[V]**.
**Validation** `jobId` `@NotNull` · `resumeLink` `@NotBlank @Size(max=2048)` **[V]**. `candidateId` is taken from JWT `sub`, never the body **[V]**.
**Create preconditions** job must exist and be `OPEN`; a non-OPEN job yields `409` via `IllegalArgumentException` **[V]**. **No duplicate guard** **[GAP-07]**.
**`PATCH /status` accepts only** `UNDER_REVIEW`, `SHORTLISTED`, `HIRED`, `REJECTED`. `APPLIED` and `WITHDRAWN` are rejected with `409` **[V]**.
**Transitions [V]** `APPLIED→UNDER_REVIEW` · `UNDER_REVIEW→SHORTLISTED` · `SHORTLISTED→HIRED` · `SHORTLISTED→REJECTED` · `{APPLIED,UNDER_REVIEW,SHORTLISTED}→WITHDRAWN`. Terminal: `HIRED`, `REJECTED`, `WITHDRAWN`.
**Side effect** transitions to `UNDER_REVIEW·SHORTLISTED·HIRED·REJECTED` trigger a best-effort notification; failure does not roll back **[V]**.

### 9.4 Recruitments — `/api/v1/recruitments`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
POST | `/` | **RECRUITER** | `{applicationId}` | `201 RecruitmentResponse` + `Location` | 400 · 401 · 403 no orgId · 404 · 409 already shortlisted · 503 |
GET | `/{recruitmentId}` | authenticated (scoped) | — | `200 RecruitmentResponse` | 401 · 404 |
GET | `/` | authenticated (scoped) | query `page,size` | `200 PagedModel<RecruitmentResponse>` | 401 |
PATCH | `/{id}/interview-status` | **RECRUITER** owner-org | `{status}` | `200 RecruitmentResponse` | 400 · 401 · 403 · 404 · 409 |
PATCH | `/{id}/final-status` | **RECRUITER** owner-org | `{status}` | `200 RecruitmentResponse` | 400 · 401 · 403 · 404 · 409 · 503 |

`RecruitmentResponse` = `{ recruitmentId, applicationId, jobId, candidateId, interviewStatus, finalStatus }`. **`ownerOrgId` is deliberately excluded** **[V]**.
**Scoping** ADMIN → all · recruiter (`orgId` present) → own org · candidate → own. Scope comes from the token; **no query parameter can widen it** **[V]**.
**Pagination** `page` floored at 0, `size` clamped to `[1,100]`, sort fixed to `recruitmentId`. **No filter parameters** **[V]**.
**`interview-status` accepts** `SCHEDULED` (from `NOT_SCHEDULED`), `COMPLETED` / `NO_SHOW` (from `SCHEDULED`) **[V]**.
**`final-status` accepts** `HIRED` / `REJECTED` from `PENDING` only; `PENDING` is always rejected **[V]**.
**Cross-service coupling — design-critical [V]:** `final-status` is `@Transactional` and calls `PATCH /applications/{id}/status` with the matching status, forwarding the caller's bearer token. Because `Application.hire()`/`reject()` require `SHORTLISTED`, the decision **fails with 409 and rolls back** unless the application is already `SHORTLISTED`. See §S-30, §S-31, **[GAP-06]**.

### 9.5 Documents — `/api/v1/documents`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
POST | `/` | **CANDIDATE** | `multipart`: part `metadata` = `{"documentType":…}`, param `file` | `201 DocumentResponse` + `Location` | 400 · 401 · 403 · 413 · 429 · 500 |
GET | `/` | **CANDIDATE** | `Pageable` (default size 20) | `200 Page<DocumentResponse>` | 401 · 403 |
GET | `/type/{documentType}` | **CANDIDATE** | path type (case-insensitive) | `200 List<DocumentResponse>` (unpaginated) | 400 unknown type · 401 · 403 |
GET | `/{documentId}` | owner **CANDIDATE** · **ADMIN** | — | `200 DocumentResponse` | 401 · 404 |
GET | `/{documentId}/content` | owner **CANDIDATE** · **ADMIN** | — | `200` binary + `Content-Type` + `Content-Disposition: inline` | 401 · 404 |
DELETE | `/{documentId}` | owner **CANDIDATE** | — | `204` | 401 · 403 · 404 |

`DocumentResponse` = `{ documentId, candidateId, documentType, fileName, contentType, fileSize:long, createdAt:Instant, updatedAt:Instant }` **[V]**.
`documentType` ∈ `RESUME · COVER_LETTER · PORTFOLIO · CERTIFICATE · OTHER` **[V]**.
**Recruiters receive 404 for every document endpoint** — explicitly deferred past R1 **[V]**, **[GAP-02]**.
Download requires the `Authorization` header → fetch as a blob; a plain anchor href will 401 **[V]**.

### 9.6 Notifications — `/api/v1/notifications`

| Method | Path | Role | Request | Success | Errors |
|---|---|---|---|---|---|
GET | `/` | authenticated (scoped to `jwt.sub`) | `Pageable` | `200 PagedModel<EntityModel<NotificationResponse>>` (HATEOAS) | 401 |
GET | `/{notificationId}` | owner | — | `200 EntityModel<NotificationResponse>` | 401 · **403 non-owner** · 404 |

`NotificationResponse` = `{ notificationId, candidateId, applicationId, eventStatus, recipientEmail, subject, message, deliveryStatus, createdAt, sentAt, errorMessage }` **[V]**.
`deliveryStatus` ∈ `PENDING · SENT · FAILED` **[V]** · `eventStatus` ∈ `UNDER_REVIEW · SHORTLISTED · HIRED · REJECTED` **[V]**.
Not role-gated: a recruiter or admin gets an empty list, not a 403 **[V]**. **No read/unread field** **[GAP-04]**.
`POST /internal/v1/notifications/application-status` is service-to-service and **not gateway-routed** — the frontend must never call it **[V]**.

### 9.7 AI — `/api/v1/ai` **[P] — not reachable from a browser**

Endpoints exist in `ai-service` but: **there is no gateway route** for `/api/v1/ai/**` **[V]**, the service is **absent from `deploy/docker-compose.yml`** **[V]**, and the only provider implementation is `MockAiProvider`, which returns deterministic hash-derived values explicitly labelled `[MOCK]` **[V]**.

For reference only — do not wire: `POST /match-score` (RECRUITER) · `POST /candidate-summary` (RECRUITER) · `POST /jd-assist` (RECRUITER, ADMIN) · `POST /resume-parse` (CANDIDATE, RECRUITER, ADMIN) · `GET /match-score/{id}` (RECRUITER, CANDIDATE, ADMIN) · `POST /match-score/{id}/override` (RECRUITER).

### 9.8 Cross-cutting

**Response headers [V]** `X-Correlation-Id` (always) · `X-RateLimit-Policy` (rate-limited routes) · security headers from the gateway `SecureHeaders` filter (`X-Content-Type-Options`, `X-Frame-Options`, HSTS).
**Request headers** `Authorization` · `Content-Type` · optional client-supplied `X-Correlation-Id` (echoed/propagated, useful for tracing a user-reported issue).
**CSRF** disabled at the gateway; the API is token-authenticated and the refresh cookie is `SameSite=Strict` **[V]**. No CSRF token handling is needed.

### 9.9 Three pagination envelopes — the frontend needs three parsers [V], [GAP-13]

```
A) PagedModel  — GET /jobs, GET /recruitments
{ "content": [ … ],
  "page": { "size":20, "number":0, "totalElements":42, "totalPages":3 } }

B) Page (Jackson) — GET /documents
{ "content": [ … ], "pageable": {…}, "totalElements":42, "totalPages":3,
  "number":0, "size":20, "first":true, "last":false, "numberOfElements":20 }

C) HATEOAS PagedModel — GET /notifications
{ "_embedded": { "notificationResponseList": [ { …, "_links": {…} } ] },
  "page": { "size":20, "number":0, "totalElements":42 },
  "_links": { "self": {…} } }

D) Unpaginated array — GET /documents/type/{t}
[ … ]
```
Normalise all four to one internal shape `{ items[], page, size, totalElements, totalPages }` at the data layer so a single `Pagination` component serves every list. Note envelope **C** omits `totalPages` — derive it as `ceil(totalElements / size)`.

---

## 10. Document upload constraints [V]

Every value below is read from `services/document-service` source and `application.yml`.

| Constraint | Value | Enforced by | Override |
|---|---|---|---|
Max file size | **10 MB** | `smarthire.document.upload.max-file-size` → `DocumentService.validateUpload`; also `spring.servlet.multipart.max-file-size` | `DOC_MAX_FILE_SIZE` |
Max request size | 10 MB | `spring.servlet.multipart.max-request-size` | `DOC_MAX_FILE_SIZE` |
Allowed content types | `application/pdf`<br>`application/msword`<br>`application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `DocumentService.isAllowedContentType` | `DOC_ALLOWED_CONTENT_TYPES` |
Minimum size | `> 0` bytes | `validateUpload` | — |
Document types | `RESUME · COVER_LETTER · PORTFOLIO · CERTIFICATE · OTHER` | `DocumentType` enum | — |
Type parsing | case-insensitive, trimmed | `DocumentType.parse` / `@JsonCreator` | — |
File name | stored sanitised, max 255 | `sanitizeFileName`, `VARCHAR(255)` | — |
Storage key | server-generated, **never** the original name | `generateStorageKey` | — |
Storage backend | local filesystem | `smarthire.document.storage.type=local` | `DOC_STORAGE_ROOT` |

**In human terms: PDF, DOC and DOCX, up to 10 MB.** That is the exact copy to use in `FileDropzone`.

**UI obligations**
1. `accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"` — convenience only; **always validate in JS too**, because the OS file picker's "All files" option bypasses `accept`.
2. Check `file.size` before upload and reject locally with the exact message in §S-16 — never make the user wait for a 413 on a 50 MB file.
3. Reject `file.size === 0` locally.
4. Match the server's content-type comparison: strip parameters (`application/pdf; charset=…`) and lower-case before comparing **[V]**.
5. Send **exactly** `metadata` (JSON part) and `file` (param). Different names produce `400 MissingServletRequestPart` / `MissingServletRequestParameter` **[V]**.
6. Error mapping: `400` unsupported type / empty / missing part · `413` over 10 MB (`MaxUploadSizeExceededException`) · `500` storage `IOException`.
7. The constraint line must be visible **before** the user picks a file, not only in an error.

**No virus scanning, no content sniffing, no image/thumbnail generation, no preview rendering** exists. Do not design a document preview pane — only metadata and raw download are available **[V]**.

---

## 11. Future AI UI — planned, not available **[P]**

### 11.1 Current reality

`ai-service` is a compiled Maven module with controllers, DTOs, a `MatchScore` entity, a Flyway migration (`ai_svc.match_scores`), PII-redaction and prompt-injection-defence services — and **no path from a browser to any of it** **[V]**:

1. The gateway has **no route** for `/api/v1/ai/**`. A call to `http://localhost:8080/api/v1/ai/...` returns 404 at the edge.
2. `ai-service` is **not** a service in `deploy/docker-compose.yml`, so it does not run in the demo stack.
3. The only `AiProvider` implementation is `MockAiProvider`: scores are `|hash(applicationId) ^ hash(jobId)| % 101` and every string it returns is prefixed `[MOCK]` and states “This is a deterministic mock response — not a real assessment.”
4. `AiProviderConfig` maps provider `claude` to a `TODO` that still yields `MockAiProvider`.

### 11.2 Non-negotiable rules for AI UI

1. **Never present a mock score as a result.** No number, bar, percentage, ring, rank, sparkline, or "87 % match" anywhere in the generated UI.
2. **Never place an AI element in a decision surface.** No AI content on S-26 (Application review) or S-30 (Recruitment workflow) in this release. Scores must not sit next to the buttons that change a person's status.
3. **Human-in-the-loop is architectural, not cosmetic** (AC-9, `docs/discovery/phase-2-product-model.md`): AI must never auto-decide. The existing `POST /match-score/{id}/override` endpoint exists precisely so a recruiter decision supersedes a score **[V]**.
4. **Label the dependency, not the feature.** “Requires the SmartHire AI service, which is not deployed in this release” — specific and verifiable, not “coming soon”.
5. **Disable, don't hide.** Generate the three shells so the eventual IA is visible and reviewable, with every control `disabled` + `aria-disabled`.

### 11.3 Planned screen shells

**S-37 · AI match scores — `/recruiter/ai/match-scores`** *(locked)*
`PlannedBanner` · H1 "AI match scores" · explanatory paragraph · a disabled form (`Application ID`, `Job ID`, disabled `Compute match score`) · an `EmptyState` reading “No scores — the AI service is not available.” **No sample row.**

**S-38 · AI job-description assistant — `/recruiter/ai/jd-assist`** *(locked)*
`PlannedBanner` · disabled fields `Role`, `Seniority`, `Tone`, `Draft job description` · disabled `Suggest improvements` · an empty, disabled result panel. (Note: even when enabled this would be advisory only — `Job` has no description field to write a suggestion into **[GAP-11]**.)

**S-39 · AI résumé insights — `/candidate/ai/resume-insights`** *(locked)*
`PlannedBanner` · disabled document picker · disabled `Extract details` · empty disabled panels for Skills / Experience / Education / Certifications.

### 11.4 When AI becomes available

The design must then add: a provider/model attribution line (`model` and `promptVersion` are already in every AI response DTO **[V]**), a visible confidence indicator (`MatchScoreResponse.confidence` **[V]**), a mandatory rationale shown with every score (`rationale` **[V]**), a recruiter override control with a reason field (`POST /match-score/{id}/override` takes `{reason}` **[V]**), and an unambiguous "advisory only — does not change any status" statement adjacent to every score.

---

## 12. Acceptance criteria

### 12.1 Global (apply to every screen)

| ID | Criterion |
|---|---|
AC-G01 | Renders without horizontal page scroll at 360, 640, 768, 1024, 1280, 1536 px. |
AC-G02 | Every interactive element is keyboard reachable and shows a visible focus ring. |
AC-G03 | Text contrast ≥ 4.5:1 (≥ 3:1 for large text and UI borders). |
AC-G04 | Has explicit loading, empty (if a list), and error renderings — none is a blank screen. |
AC-G05 | Navigation reflects `roles[]` from `GET /auth/me`; no control appears that the role cannot use. |
AC-G06 | A 401 triggers one silent-refresh attempt, then `/session-expired?next=…`. |
AC-G07 | A 403 routes to `/403` naming the signed-in email and role. |
AC-G08 | A 404 or privacy-masked response routes to `/404` without revealing whether the resource exists. |
AC-G09 | A 500 shows the generic message plus a copyable `correlationId`. |
AC-G10 | No write request is auto-retried. |
AC-G11 | Access token is held in memory only — absent from `localStorage`, `sessionStorage`, and the URL. |
AC-G12 | One `<h1>`, no skipped heading levels, landmarks present, `Skip to content` is first in tab order. |
AC-G13 | All status values render via `StatusBadge` with the §7.2 tokens and a visible text label. |
AC-G14 | Every UUID shown to a user is a `CopyableId`. |
AC-G15 | No screen displays invented data: no mock AI output, no placeholder job description, no fabricated counts. |
AC-G16 | `prefers-reduced-motion: reduce` disables transitions and shimmer. |

### 12.2 Per screen

| Screen | Acceptance criteria |
|---|---|
**S-00 Landing** | Three workspace cards present · each auth link carries `?role=` · public jobs link works without an account · no credentials or invitation codes rendered. |
**S-01 Job browse** | Anonymous load succeeds with no token · filters `q`/`location`/`status` reach the server and live in the URL · size selector maxes at 100 · no sortable headers · empty-with-filters offers `[Clear filters]` · `status` control hidden for anonymous/candidate, shown for recruiter. |
**S-02 Job detail** | OPEN renders for anonymous · DRAFT/CLOSED for a non-owner renders `/404` · apply panel reflects status exactly (OPEN enabled, CLOSED/DRAFT disabled with reason) · anonymous sees `Sign in to apply` carrying `next` · only the four real job fields appear, with the gap note. |
**S-03 Apply** | `resumeLink` enforces required + ≤2048 · `candidateId` never in the payload · 409 shows “no longer accepting applications” and refreshes the job · 201 stores `applicationId` locally and routes to S-12 · duplicate warning appears when the local registry already holds this `jobId`. |
**S-04 Sign in** | 401 shows one neutral message, clears password, keeps email · no 12-char minimum applied · no “Forgot password?” link · 429 disables submit for 10 s with a countdown · success routes by the server role and honours a permitted `next` · a generic `/login` link never shows a mismatch notice · an explicit `?role=` mismatch opens the real workspace with a dismissible notice and keeps the session. |
**S-05 Register** | Role selector switches copy and fields · password rejects < 12 characters client-side · recruiter requires an organization name · administrator requires an invitation code · 409 shows an email field error with `Sign in instead` · 403 administrator denial is a form-level alert that never echoes the code · no auto-login attempt · success routes to `/login?role={role}` with the email prefilled. |
**S-11 My applications** | `GapNotice` present and dismissible · rows merge recruitment-derived and locally tracked ids, de-duplicated · each row's status comes from `GET /applications/{id}` · `Source` column distinguishes provenance · `Add by ID` validates the UUID and the fetch · empty state offers `[Browse jobs]`. |
**S-12 Application detail** | Timeline marks the true current status · Withdraw shown **only** for `APPLIED`/`UNDER_REVIEW`/`SHORTLISTED` · confirm dialog is destructive · 409 explains and refreshes · “What happens next” copy matches the status · linked recruitment link appears only when one is found. |
**S-13 My progress** | Interview and decision render as two independent badges · pagination is server-driven · no filter controls (endpoint supports none) · empty state explains how rows appear. |
**S-14 Progress detail** | Two separate timelines · no invented interview date/time/interviewer · no `ownerOrgId` displayed · non-owner gets `/404`. |
**S-15 Documents** | `All` tab paginated, type tabs unpaginated with pagination hidden · download fetches with `Authorization` as a blob · size and date formatted · delete confirms destructively. |
**S-16 Upload** | Parts named exactly `metadata` and `file` · client blocks >10 MB, empty, and disallowed MIME before any request · constraint line visible before selection · 413 and 400 map to the specified copy · success refetches and highlights the new row. |
**S-18 Notifications** | No unread dots, no badge count, no `Mark all read` · `deliveryStatus` and `eventStatus` render as distinct chips · nav item absent for RECRUITER/ADMIN · empty state explains the trigger. |
**S-19 Notification detail** | Non-owner renders `/403` (not `/404`) · `FAILED` reveals `errorMessage` · links to the related application. |
**S-20/32/36 Profile** | `null` `orgId` renders `—` with explanatory text · no edit or change-password affordance · gap notice explains why · session card shows a live expiry countdown and `Refresh now`. |
**S-21 Job postings** | Each row shows exactly one lifecycle action matching its status · lifecycle/edit actions hidden for rows not owned by the caller's org · scope toggle sends `mine=true` and keeps paging/counts server-accurate · recruiter status filter offers all three values. |
**S-22 Create job** | All three fields enforce `@NotBlank` + 255 · info callout states DRAFT · `Save and publish` is two calls, and a failed publish keeps the draft and shows the warning banner with `[Try publishing again]`. |
**S-23 Job detail (recruiter)** | One status-correct CTA · `Close` confirms · 409 refetches and re-renders the CTA without auto-retry · Applications card shows the gap notice and an ID-entry control · visibility copy matches the real rule. |
**S-24 Edit job** | Status is read-only with a pointer to the detail page · 403 renders inline, not as a redirect · dirty-form guard active. |
**S-25 App lookup** | Gap notice present · UUID validated before the call · `404` copy does not reveal whether the id exists · recently-viewed list is local and capped at 10. |
**S-26 App review** | Buttons expose exactly the legal transitions for the current status (`APPLIED`→Start review; `UNDER_REVIEW`→Shortlist; `SHORTLISTED`→Mark hired/Reject; terminal→none) · never offers `APPLIED` or `WITHDRAWN` · candidate shown as `CopyableId` with the identity gap notice · résumé link carries the recruiter-access warning · 409 renders the illegal-transition panel using `currentStatus`/`attemptedAction` · confirm copy says the candidate *will be notified* without promising delivery. |
**S-28 Shortlist** | Warning callout appears when the application is not yet `SHORTLISTED` · 409 offers `[Open it]` · 403 explains the missing `orgId` · success routes to the new recruitment. |
**S-29 Recruitments** | Server-side pagination · filter chips labelled as page-local · job context batched and cached per page · no write actions for ADMIN. |
**S-30 Recruitment detail** | Interview CTAs match the aggregate exactly · interview and decision are independent (decision never gated on interview) · the precondition banner appears, and `[Record decision]` is disabled, whenever `application.status !== 'SHORTLISTED'` · the "status only, no date" note is present. |
**S-31 Final decision** | `PENDING` not selectable · callout states the application update and notification · `ApplicationSyncConflict` 409 says the decision was **not** applied and links to the application · 503 says nothing has changed · success is treated as final and locks the card. |
**S-33 Admin overview** | `GET /auth/admin/whoami` 403 routes to `/403` · recruitment total from a real call · gap notice about absent admin tooling · **no placeholder KPI tiles**. |
**S-34 All recruitments** | Admin scope shows all orgs · no write controls rendered at all. |
**S-35 Lookup** | Three working by-id lookups · document download available (admin may read content) · gap notice explains the absence of listing. |
**S-37/38/39 AI** | `PlannedBanner` present · every control `disabled` + `aria-disabled` · **zero** numeric scores, sample rationales, or fabricated parsed fields · no AI element on S-26 or S-30. |

### 12.3 Core user journeys

| ID | Journey | Pass condition |
|---|---|---|
**J-01** Candidate onboarding | `/jobs` → `/jobs/:id` → `Sign in to apply` → `/register` → `/login` → back to `/jobs/:id/apply` → submit → S-12 shows `APPLIED`. `next` survives registration and login. |
**J-02** Recruiter publishes | Login → `/recruiter/jobs/new` → create (DRAFT) → S-23 → `Publish posting` → `OPEN` → the posting appears in anonymous `/jobs`. |
**J-03** Full hiring pipeline | Recruiter opens the application → `Start review` (`UNDER_REVIEW`) → `Shortlist` (`SHORTLISTED`) → `Shortlist into recruitment` → `Mark interview scheduled` → `Mark completed` → `Record decision: Hired` → **succeeds**, application becomes `HIRED`. |
**J-04** Pipeline ordering guard | From an `APPLIED` application, shortlist into a recruitment, then attempt `Record decision`. The button is **disabled** and the precondition banner directs the recruiter to the application. If forced (e.g. via a stale tab), the 409 states the decision was not applied. **This is the journey that proves [GAP-06] is handled, not hidden.** |
**J-05** Candidate tracks outcome | After J-03, the candidate sees `HIRED` on S-12, a completed/hired pair on S-14, and a `HIRED` notification on S-18. |
**J-06** Candidate withdraws | From `APPLIED`/`UNDER_REVIEW`/`SHORTLISTED`, withdraw → confirm → status `WITHDRAWN`, withdraw control gone, no further transitions offered. |
**J-07** Document lifecycle | Upload a 2 MB PDF → appears in the list → download returns the same file → filter by `RESUME` → delete with confirmation → removed. |
**J-08** Upload rejection | Attempt a `.png`, an 11 MB PDF, and a 0-byte file. All three are rejected **client-side** with the §S-16 messages and no network request. |
**J-09** Session expiry | Idle past access-token expiry, then act. Either silent refresh succeeds transparently, or `/session-expired` appears with a working `next`. No raw 401 is ever shown. |
**J-10** Authorization boundary | A candidate navigating to `/recruiter/jobs/new` and a recruiter navigating to `/candidate/documents` both land on `/403` with their role named. Neither sees the other's nav items. |
**J-11** Privacy masking | Recruiter A requests recruiter B's DRAFT job, another candidate's application, and any document. All render `/404` with identical copy — nothing reveals existence. |
**J-12** Conflict recovery | With the same job open in two tabs, publish in tab 1 then publish in tab 2. Tab 2 shows the illegal-transition panel; `[Refresh and try again]` restores a correct CTA. |
**J-13** Dependency outage | With job-service stopped, applying shows “Your application was not submitted.” + `[Retry]`; with application-service stopped, a final decision shows “Nothing has changed.” |
**J-14** Rate limit | Rapid repeated sign-in attempts produce the 429 banner and a 10 s countdown, and recover afterwards. |
**J-15** Keyboard-only | Complete J-02 using only the keyboard, including dialogs, with focus always visible and returned to the trigger on dialog close. |

---

## 13. Backend gap register

The authoritative list. Every gap is reflected in a screen treatment above; none is silently assumed away.

| ID | Gap | Verified evidence | Frontend impact | Treatment in this spec | Endpoint that would close it |
|---|---|---|---|---|---|
**GAP-01** | **No application list/search endpoint.** | `ApplicationController` has only `POST /`, `GET /{id}`, `POST /{id}/withdraw`, `PATCH /{id}/status`. `ApplicationService.listForCandidate` and `ApplicationRepository.findByCandidateId`/`findByJobId` exist but are unwired. | **Severe.** Candidate “My applications” and recruiter “applications for this job” — the two most-expected screens — cannot be served. | S-11 composes recruitment-derived rows + a local registry, labelled by provenance. S-25 is an ID-lookup utility. | `GET /api/v1/applications?jobId&status&page&size`, caller-scoped. |
**GAP-02** | **Recruiters cannot read documents.** | `DocumentService.get` returns 404 for any non-owner non-admin; source comment: “For R1 MVP, recruiters cannot directly access documents”. | Recruiters cannot open a candidate's résumé. Review relies on the free-text `resumeLink`. | S-26 Résumé card carries an explicit warning; S-03 warns when a document URL is chosen. | Recruiter read via recruitment ownership. |
**GAP-03** | **No admin management API.** | Recruiters and administrators can self-register (administrators with `AUTH_ADMIN_SIGNUP_CODE`), and company endpoints manage company records and memberships. There is no user, role, or organization administration API. | No user/org admin screens are possible. | S-33 and S-35 carry gap notices; no invented KPI tiles. | `GET/POST /api/v1/admin/users`, `/organizations`. |
**GAP-04** | **Notifications have no read state.** | `Notification` entity has no `read`/`readAt` column; no mutating endpoint. | No unread badge, count, or mark-read. | S-18 omits all unread affordances. | `PATCH /api/v1/notifications/{id}/read` + a `read` field. |
**GAP-05** | **`resumeLink` is unlinked free text.** | `Application.resumeLink` is `String` (≤2048); no FK or validation against `document_svc`. | A candidate can submit any URL; no server guarantee it resolves or is theirs. | S-03 offers a client-side document picker with an explicit caveat. | `documentId` on `ApplicationCreateRequest`, resolved server-side. |
**GAP-06** | **Hidden ordering coupling on final status.** | `RecruitmentService.updateFinalStatus` → `PATCH /applications/{id}/status`; `Application.hire()`/`reject()` require `SHORTLISTED`. The Postman Review-1 collection goes Apply → Recruitment → Scheduled → Completed → **HIRED** with no application status steps, so that flow hits `409`. | A recruiter following the obvious path gets an opaque 409. | S-30 precondition banner + disabled CTA; S-28 warning callout; J-04 tests it; S-31 maps the error precisely. | Either have `shortlist` require `SHORTLISTED`, or have `final-status` drive the intermediate transitions. |
**GAP-07** | **No duplicate-application guard.** | No unique constraint on `(job_id, candidate_id)`; no existence check in `ApplicationService.create`. | A candidate can apply repeatedly to one job. | S-03 shows a local-registry warning with `[Submit anyway]`; §8.9 forbids auto-retrying `POST`. | Unique constraint + `409` on duplicate. |
**GAP-08** | **`Secure` refresh cookie breaks local silent refresh.** | `ResponseCookie…secure(true).sameSite("Strict").path("/api/v1/auth")`; the demo stack serves plain HTTP. | Over `http://localhost` the cookie is not stored; sessions end hard at 15 minutes. | `/session-expired` (S-06) is a designed state; §8.6 documents the constraint. | Serve over HTTPS locally, or make `secure` profile-conditional. |
**GAP-09** | **No password reset / change / email verification.** | `AuthController` exposes only register, login, refresh, logout, me, admin/whoami. | A locked-out user has no self-service path. | S-04 omits "Forgot password?"; profile explains there is no self-service reset. | `POST /auth/password/forgot`, `/reset`, `/change`. |
**GAP-10** | **No profile update.** | `User` holds only `email`, `passwordHash`, `role`, `orgId`, `createdAt`; `MeResponse` is read-only. | No display name, phone, or avatar anywhere; profile is read-only. | S-20/32/36 are read-only with a gap notice. | `PATCH /api/v1/auth/me` + profile fields. |
**GAP-11** | **Job has only four descriptive fields.** | `Job` = `jobId, companyName, role, location, status, ownerOrgId`. | No description, salary, employment type, skills, or deadline can be shown. | S-02 renders a four-field `DefinitionList` plus a gap note — no placeholder prose. | Extend `Job` + its DTOs. |
**GAP-12** | **Application captures nothing but a link.** | `ApplicationCreateRequest` = `{jobId, resumeLink}`. | No cover letter, screening answers, or availability. | S-03 has a single field; no invented inputs. | Extend `ApplicationCreateRequest`. |
**GAP-13** | **Three pagination envelopes, no sort parameter.** | `PagedModel` (jobs, recruitments), Jackson `Page` (documents), HATEOAS `PagedModel` (notifications), raw `List` (documents by type). Sort is hard-coded to `jobId` / `recruitmentId`. | Three parsers needed; no sortable columns possible. | §9.9 normalisation layer; §6.4 forbids sortable headers. | One envelope + a `sort` parameter. |
**GAP-14** | **Recruitment filtering is incomplete.** | Jobs support `mine=true` for caller-organization scoping (recruiters only); recruitments still accept only `page`/`size`; no distinct-locations endpoint. | Recruiter job lists are now server-scoped, but recruitment status filters must remain client-side, post-paging, so counts can mislead. | S-21 uses `mine=true`; S-29 labels client-side filters and states "{n} of {pageSize}". | `interviewStatus`/`finalStatus` on recruitments. |
**GAP-15** | **Candidate identity is a UUID only.** | No endpoint resolves another user's id. `MeResponse` covers the caller alone. | Recruiters and admins can never see a candidate's name or email. | `CopyableId` + gap notice on S-26 and S-29. | `GET /api/v1/users/{id}` returning a minimal public profile. |
**GAP-16** | **Interview status carries no schedule.** | `InterviewStatus` is an enum; `Recruitment` has no date, time, location, or interviewer column. | No calendar, no reminders, no interview detail. | S-14/S-30 state that scheduling happens outside the platform. | Add `scheduledAt`, `interviewerId`, `location`. |
**GAP-17** | **Registration conflict documentation corrected.** | `AuthExceptionHandler` maps `EmailAlreadyExistsException` → `409`; `AuthController` now advertises `409` for a duplicate email, so the earlier mismatch is closed. | None; clients handle `409`. | S-05 handles `409`. | — (closed). |
**GAP-18** | **No job delete.** | `JobController` has no `DELETE`. | A mistaken draft cannot be removed. | S-22 keeps a draft after a failed publish rather than claiming a rollback. | `DELETE /api/v1/jobs/{jobId}` for DRAFT only. |
**GAP-19** | **AI service unreachable.** | No gateway route for `/api/v1/ai/**`; absent from `docker-compose.yml`; `MockAiProvider` is the only provider; `claude` branch is a `TODO` falling back to mock. | No AI capability can ship. | §11 — three locked shells, no mock output rendered. | Add the route, add the compose service, implement a real provider. |
**GAP-20** | **`README` / product-model drift.** | `docs/discovery/phase-2-product-model.md` AC-2 says a posted job is created with `status=OPEN`; the implementation creates `DRAFT` and requires an explicit `POST /{id}/open`. | A spec-trusting UI would omit the publish step. | S-22/S-23 implement the real two-step flow. | Reconcile the document with the code. |

**Severity for frontend completeness:** GAP-01 (blocking two primary screens) > GAP-06 (silently breaks the happy path) > GAP-15 and GAP-02 (degrade recruiter review) > GAP-13 and GAP-14 (implementation cost) > the rest (scope limits).

---

## 14. Google Stitch generation guide

### 14.1 Generation order

Generate in this sequence. Each phase establishes conventions the next phase reuses; generating out of order produces drift.

**Phase 0 — Foundations (no screens).**
Register the design system first: all §7.2 colour tokens, the §7.3 type scale, §7.4 spacing/radius/elevation, and the §7.5 interaction-state table. Then generate the primitives as standalone components in this order: `Button` → `Input`/`Select`/`Textarea` → `StatusBadge` → `Card` → `DefinitionList` → `CopyableId` → `Skeleton` → `EmptyState` → `ErrorPanel` → `GapNotice` → `Toast` → `Dialog`/`ConfirmDialog` → `DataTable` → `Pagination` → `FilterBar` → `StatusTimeline` → `FileDropzone` → `Breadcrumb` → `RoleChip` → `PlannedBanner`.
`StatusBadge` must be generated **before any screen**, with all 16 enum values from §7.2 baked in. It appears on 20+ screens; defining it late guarantees inconsistency.

**Phase 1 — Shells.** App shell (§4.1) → public shell (§4.2) → auth shell (§4.3). Include the sidebar's three role variants and all responsive behaviours from §4.4.

**Phase 2 — Auth and system states.** S-04 → S-05 → S-06 → S-07 → S-08 → S-09 → S-10. Small, self-contained, and they lock in form, error, and empty-state conventions cheaply.

**Phase 3 — Public.** S-01 → S-02. Establishes the list→detail pattern, `FilterBar`, `Pagination`, and `JobCard`.

**Phase 4 — Candidate.** S-15 → S-16 → S-17 → S-12 → S-03 → S-13 → S-14 → S-18 → S-19 → S-11 → S-20.
Documents come first because they are the most completely supported candidate flow (list, upload, download, delete, filter) and they exercise the table, dialog, dropzone, and pagination patterns on solid ground. **S-11 is generated last** in this phase: it is a composition of the others and the most gap-constrained screen.

**Phase 5 — Recruiter.** S-21 → S-22 → S-23 → S-24 → S-26 → S-27 → S-28 → S-29 → S-30 → S-31 → S-25 → S-32.
Jobs before applications (they are fully supported). S-30 and S-31 come after S-26 so the precondition relationship is already understood. S-25 last, like S-11, because it is a gap workaround.

**Phase 6 — Admin.** S-33 → S-34 → S-35 → S-36. S-34 reuses S-29's table wholesale with writes removed.

**Phase 7 — Planned AI (locked).** S-37 → S-38 → S-39. Generate last, so there is no chance of AI patterns leaking into decision screens.

### 14.2 Consistency that must be preserved across every screen

1. **One status vocabulary.** The 16 values in §7.2 are the only status strings in the system. Never invent `PENDING_REVIEW`, `INTERVIEWING`, `OFFER`, `ARCHIVED`, or similar.
2. **One badge component** for all of them, always with its text label.
3. **One pagination component**, max page size 100, no sortable headers anywhere.
4. **One error vocabulary** — 400 field errors · 401 refresh-then-interstitial · 403 `/403` with role named · 404 ambiguous copy · 409 illegal-transition panel · 429 countdown banner · 500 generic + correlation id · 503 “nothing has changed”.
5. **One empty-state shape**, with screen-specific copy from §5 — never a generic “No data”.
6. **One gap treatment** — `GapNotice`, never placeholder rows, zeroed counts, or lorem text.
7. **Legal transitions only.** Every action button corresponds to a transition the aggregate permits from the current state. Illegal moves are *absent*, not disabled, except where §5 explicitly specifies a disabled control plus a reason.
8. **UUIDs are always `CopyableId`**, truncated to 8 characters in tables, full on detail screens.
9. **Role-derived navigation** — identical sidebar component, three item sets, driven by `roles[]`.
10. **Primary action placement** — top-right of the page header on desktop; full-width below the title at `sm`; sticky bottom bar at `xs`. One primary action per view.
11. **Destructive confirmation** — always `ConfirmDialog`, danger button, cancel focused first.
12. **Table → card list** at ≤767 px, never horizontal scroll.
13. **Focus rings never removed**, on any element, in any state.
14. **Independent lifecycles stay independent** — interview status and final status are two badges and two timelines, never merged into a single progress indicator.

### 14.3 Prompting notes for Stitch

- Give Stitch §7 verbatim as the design-system brief before requesting any screen.
- Request one screen per prompt, naming its S-ID, and include that screen's §5 block plus the components it needs by name.
- For gap-affected screens (S-11, S-25, S-33, S-35, S-37/38/39), **state the prohibition explicitly** in the prompt: “Do not generate sample rows, placeholder values, or numeric scores. Render the `GapNotice` component with this exact copy: …”. Generation tools default to filling space with plausible fake data; this is the single most likely failure mode for this document.
- When a screen reuses a component already generated, reference it by name rather than re-describing it.
- After each phase, verify against §12 before proceeding.

### 14.4 Build-time environment facts [V]

| Fact | Value |
|---|---|
API base URL | `http://localhost:8080` (gateway) |
Expected frontend dev origin | `http://localhost:5173` (`GATEWAY_CORS_ALLOWED_ORIGINS` default) |
Credentials mode | `include` (required for the refresh cookie) |
Aggregated API docs | `http://localhost:8080/swagger-ui.html` |
Per-service specs | `http://localhost:8080/v3/api-docs/{auth,job,application,recruitment,document,notification}-service` |
Service registry dashboard | `http://localhost:8761` |
Stack recommended by architecture | Vite + React + TypeScript, React Query, React Router, Zod (ADR-0014) |

Secrets, seeded demo credentials, and `deploy/.env` values are deliberately **not** reproduced in this document. They must never be hardcoded, prefilled into a form, or committed to the frontend.

---

## 15. Change control

This document is derived from source. When any of the following change, update it and re-verify the affected §12 criteria:

- A controller mapping, `@PreAuthorize`, or DTO validation annotation.
- The role-aware registration contract (`role`, `organizationName`, `adminInviteCode`) or `AUTH_ADMIN_SIGNUP_CODE` availability.
- The sign-in role-intent/mismatch behaviour or the `/workspace` role precedence.
- A domain enum value or a state-machine guard.
- A gateway route, security matcher, rate limit, or CORS setting.
- `smarthire.document.upload.*` or multipart limits.
- A job-listing visibility filter such as `mine=true`.
- Any gap in §13 being closed — each row names the endpoint that would close it and the screens to revisit.

Closing **GAP-01** alone reshapes S-11 and S-25 from gap workarounds into ordinary paginated tables; design both so that change is a data-layer swap, not a redesign.
