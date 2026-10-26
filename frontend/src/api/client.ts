// SmartHire API client.
//
// All traffic goes through the API Gateway (http://localhost:8080 in development,
// proxied by Vite). The access token is held in memory only — never in
// localStorage or sessionStorage — and the refresh token lives in an HttpOnly
// cookie the browser manages.

import type { AccountRole } from '../utils/roles';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

/* ============================================================ Error shapes */

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  correlationId: string;
  /** Present on illegal application-state transitions (409). */
  currentStatus?: string;
  attemptedAction?: string;
  /** Present on illegal recruitment-state transitions (409). */
  currentInterviewStatus?: string;
  currentFinalStatus?: string;
}

export class ApiErrorResponse extends Error {
  constructor(public readonly error: ApiError) {
    super(error.detail);
    this.name = 'ApiErrorResponse';
  }
}

/** Narrowing helper so callers do not have to re-check the class. */
export function isApiError(error: unknown): error is ApiErrorResponse {
  return error instanceof ApiErrorResponse;
}

/** HTTP status of a caught error, or undefined when it is not an API error. */
export function apiErrorStatus(error: unknown): number | undefined {
  return isApiError(error) ? error.error.status : undefined;
}

/* ====================================================== Pagination shapes */
//
// The backend exposes three different envelopes. They are normalised here so
// pages do not each re-implement the parsing.

/** Spring Data `PagedModel` — used by /jobs and /recruitments. */
export interface PagedModel<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Raw Jackson-serialised Spring `Page` — used by /documents. */
export interface Page<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: { sorted: boolean; unsorted: boolean; empty: boolean };
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  sort: { sorted: boolean; unsorted: boolean; empty: boolean };
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}

/** HATEOAS `PagedModel` — used by /notifications. */
export interface HateoasPagedModel<T> {
  _embedded: { notificationResponseList?: T[]; items?: T[] };
  _links: {
    first?: { href: string };
    last?: { href: string };
    next?: { href: string };
    prev?: { href: string };
    self?: { href: string };
  };
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
}

/** One shape every list screen can consume. */
export interface NormalisedPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function normalisePagedModel<T>(model: PagedModel<T>): NormalisedPage<T> {
  return {
    items: model.content ?? [],
    page: model.page?.number ?? 0,
    size: model.page?.size ?? 0,
    totalElements: model.page?.totalElements ?? 0,
    totalPages: model.page?.totalPages ?? 0,
  };
}

export function normalisePage<T>(model: Page<T>): NormalisedPage<T> {
  return {
    items: model.content ?? [],
    page: model.number ?? 0,
    size: model.size ?? 0,
    totalElements: model.totalElements ?? 0,
    totalPages: model.totalPages ?? 0,
  };
}

export function normaliseHateoas<T>(model: HateoasPagedModel<T>): NormalisedPage<T> {
  const embedded = model._embedded ?? {};
  const items = embedded.notificationResponseList ?? embedded.items ?? [];
  const size = model.page?.size ?? (items.length || 1);
  const totalElements = model.page?.totalElements ?? items.length;
  // The notifications envelope omits totalPages; derive it.
  const totalPages = model.page?.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 0);
  return { items, page: model.page?.number ?? 0, size, totalElements, totalPages };
}

/* ======================================================= Token management */

let accessToken: string | null = null;
let accessTokenExpiresAt = 0;

/** Refresh 60s before expiry so an in-flight request never races the clock. */
const EXPIRY_SKEW_MS = 60_000;

/** Listener invoked when authentication is irrecoverably lost. */
type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

export function getAccessToken(): string | null {
  if (!accessToken) return null;
  // Fail closed when the expiry is unknown: a token that cannot be shown to be live must not be
  // attached to a request. The refresh path is what recovers a valid session.
  if (!Number.isFinite(accessTokenExpiresAt) || accessTokenExpiresAt <= 0) return null;
  if (Date.now() < accessTokenExpiresAt - EXPIRY_SKEW_MS) {
    return accessToken;
  }
  return null;
}

/** The raw token even if it is close to expiry — used by the refresh scheduler. */
export function peekAccessToken(): string | null {
  return accessToken;
}

/**
 * Reads the `exp` claim from a JWT without verifying it.
 *
 * The claim is only used as a fallback clock when the login response carried no usable
 * `accessTokenExpiresAt`. Verification remains the resource servers' job; this value never
 * decides whether a token is authentic, only whether it is worth attaching to a request.
 */
function jwtExpiryMillis(token: string): number {
  const parts = token.split('.');
  if (parts.length !== 3) return 0;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const payload = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp)
      ? payload.exp * 1000
      : 0;
  } catch {
    return 0;
  }
}

export function setAccessToken(token: string, expiresAt: string | number): void {
  accessToken = token;
  const parsed = typeof expiresAt === 'number' ? expiresAt : new Date(expiresAt).getTime();
  accessTokenExpiresAt =
    Number.isFinite(parsed) && parsed > 0 ? parsed : jwtExpiryMillis(token);
}

export function clearAccessToken(): void {
  accessToken = null;
  accessTokenExpiresAt = 0;
}

export function isTokenExpired(): boolean {
  return (
    !accessToken ||
    !Number.isFinite(accessTokenExpiresAt) ||
    accessTokenExpiresAt <= 0 ||
    Date.now() >= accessTokenExpiresAt
  );
}

export function accessTokenExpiry(): number {
  return accessTokenExpiresAt;
}

/* ==================================================== Response handling */

async function toApiError(response: Response, fallbackDetail: string): Promise<ApiError> {
  const correlationId = response.headers.get('X-Correlation-Id') ?? '';
  try {
    const body = (await response.json()) as Partial<ApiError>;
    return {
      type: body.type ?? 'about:blank',
      title: body.title ?? response.statusText ?? 'Request failed',
      status: body.status ?? response.status,
      detail: body.detail ?? fallbackDetail,
      instance: body.instance ?? response.url,
      correlationId: body.correlationId ?? correlationId,
      currentStatus: body.currentStatus,
      attemptedAction: body.attemptedAction,
      currentInterviewStatus: body.currentInterviewStatus,
      currentFinalStatus: body.currentFinalStatus,
    };
  } catch {
    return {
      type: 'about:blank',
      title: response.statusText || 'Request failed',
      status: response.status,
      detail: fallbackDetail,
      instance: response.url,
      correlationId,
    };
  }
}

async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204 || response.status === 205) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  // Covers application/json, application/problem+json and the
  // application/hal+json media type Spring HATEOAS services reply with.
  if (contentType.includes('json')) {
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  if (
    contentType.startsWith('application/octet-stream') ||
    contentType.startsWith('image/') ||
    contentType.startsWith('application/pdf') ||
    contentType.startsWith('application/msword') ||
    contentType.startsWith(
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    )
  ) {
    return (await response.blob()) as T;
  }
  return (await response.text()) as T;
}

/* ===================================================== Silent refresh */

// Single-flight: concurrent 401s share one refresh so a dashboard firing several
// parallel calls cannot burn multiple rotations of the refresh token.
let refreshInFlight: Promise<boolean> | null = null;

export function tryRefreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });
      if (!response.ok) {
        clearAccessToken();
        return false;
      }
      const data = (await response.json()) as TokenResponse;
      setAccessToken(data.accessToken, data.accessTokenExpiresAt);
      return true;
    } catch {
      clearAccessToken();
      return false;
    } finally {
      // Release the gate on the next tick so waiting callers observe the result.
      setTimeout(() => {
        refreshInFlight = null;
      }, 0);
    }
  })();

  return refreshInFlight;
}

/**
 * Rebuilds an access token from the refresh cookie.
 *
 * Returns false when there is no usable refresh cookie, which is the normal
 * outcome for a signed-out visitor and for a local HTTP origin where the
 * `Secure` cookie is not stored. Callers treat false as "signed out", never as
 * an error to surface.
 */
export async function restoreSession(): Promise<boolean> {
  return tryRefreshSession();
}

/**
 * End the server-side session, then clear the in-memory access token.
 *
 * The logout endpoint is authenticated, so this must go through the normal
 * request path: it attaches the bearer token (refreshing first if it has
 * expired) and only then calls logout. A bare `fetch` here used to produce a
 * 401, leave the HttpOnly refresh cookie alive, and silently sign the user
 * back in on the next full page load.
 */
export async function endSession(): Promise<void> {
  try {
    await request<void>('/api/v1/auth/logout', { method: 'POST' });
  } catch {
    // A failed logout must still clear client state. If the refresh token was
    // already invalid the next restoreSession() also fails, so the stale cookie
    // cannot resurrect the session.
  } finally {
    clearAccessToken();
  }
}

/* ============================================================ Core fetch */

interface RequestOptions extends RequestInit {
  /** Suppress the automatic refresh-and-retry (used by the refresh call itself). */
  skipAuthRetry?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuthRetry = false, ...init } = options;
  const token = getAccessToken();

  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (response.status === 401 && !skipAuthRetry) {
    const refreshed = await tryRefreshSession();
    if (refreshed) {
      return request<T>(path, { ...options, skipAuthRetry: true });
    }
    onUnauthorized?.();
  }

  if (!response.ok) {
    throw new ApiErrorResponse(await toApiError(response, 'An unexpected error occurred.'));
  }

  return parseBody<T>(response);
}

/** Build a query string, omitting empty values so the server sees clean input. */
function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
}

/* ============================================================ Auth API */

export interface MeResponse {
  sub: string;
  email: string;
  roles: string[];
  orgId: string | null;
}

export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
}

export interface RegisterResponse {
  userId: string;
}

/** Payload accepted by the role-aware registration endpoint. */
export interface RegisterPayload {
  email: string;
  password: string;
  role: AccountRole;
  /** Required for RECRUITER; rejected for the other roles. */
  organizationName?: string;
  /** Required for ADMIN; never persisted, logged or echoed. */
  adminInviteCode?: string;
}

export const authApi = {
  login: async (email: string, password: string): Promise<TokenResponse> => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
      credentials: 'include',
    });

    if (!response.ok) {
      throw new ApiErrorResponse(
        await toApiError(response, 'Incorrect email or password.')
      );
    }

    const data = (await response.json()) as TokenResponse;
    setAccessToken(data.accessToken, data.accessTokenExpiresAt);
    return data;
  },

  register: async (payload: RegisterPayload): Promise<RegisterResponse> => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: payload.email,
        password: payload.password,
        role: payload.role,
        organizationName: payload.organizationName?.trim() || undefined,
        adminInviteCode: payload.adminInviteCode?.trim() || undefined,
      }),
      credentials: 'include',
    });

    if (!response.ok) {
      throw new ApiErrorResponse(
        await toApiError(response, 'Registration failed. Please try again.')
      );
    }

    return (await response.json()) as RegisterResponse;
  },

  refresh: async (): Promise<TokenResponse> => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    });

    if (!response.ok) {
      clearAccessToken();
      throw new ApiErrorResponse(
        await toApiError(response, 'Your session has expired.')
      );
    }

    const data = (await response.json()) as TokenResponse;
    setAccessToken(data.accessToken, data.accessTokenExpiresAt);
    return data;
  },

  logout: endSession,

  me: async (): Promise<MeResponse> => request<MeResponse>('/api/v1/auth/me'),
};

/* ============================================================ Jobs API */

export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED';

export interface JobResponse {
  jobId: string;
  companyName: string;
  role: string;
  location: string;
  status: JobStatus;
  ownerOrgId: string;
}

export interface JobCreateRequest {
  companyName: string;
  role: string;
  location: string;
}

export interface JobUpdateRequest {
  companyName: string;
  role: string;
  location: string;
}

export interface JobListParams {
  q?: string;
  location?: string;
  status?: string;
  /** Restrict to the caller's own organization; only meaningful for recruiters. */
  mine?: boolean;
  page?: number;
  size?: number;
}

export const jobsApi = {
  list: async (params: JobListParams = {}): Promise<PagedModel<JobResponse>> =>
    request<PagedModel<JobResponse>>(
      `/api/v1/jobs${query({
        q: params.q,
        location: params.location,
        status: params.status,
        mine: params.mine,
        page: params.page,
        size: params.size,
      })}`
    ),

  get: async (jobId: string): Promise<JobResponse> =>
    request<JobResponse>(`/api/v1/jobs/${jobId}`),

  create: async (data: JobCreateRequest): Promise<JobResponse> =>
    request<JobResponse>('/api/v1/jobs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),

  update: async (jobId: string, data: JobUpdateRequest): Promise<JobResponse> =>
    request<JobResponse>(`/api/v1/jobs/${jobId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),

  /** DRAFT -> OPEN */
  open: async (jobId: string): Promise<JobResponse> =>
    request<JobResponse>(`/api/v1/jobs/${jobId}/open`, { method: 'POST' }),

  /** OPEN -> CLOSED */
  close: async (jobId: string): Promise<JobResponse> =>
    request<JobResponse>(`/api/v1/jobs/${jobId}/close`, { method: 'POST' }),

  /** CLOSED -> OPEN */
  reopen: async (jobId: string): Promise<JobResponse> =>
    request<JobResponse>(`/api/v1/jobs/${jobId}/reopen`, { method: 'POST' }),
};

/* ===================================================== Applications API */

export type ApplicationStatus =
  | 'APPLIED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApplicationResponse {
  applicationId: string;
  jobId: string;
  candidateId: string;
  resumeLink: string;
  status: ApplicationStatus;
}

export interface ApplicationCreateRequest {
  jobId: string;
  resumeLink: string;
}

export interface ApplicationStatusUpdateRequest {
  status: string;
}

export const applicationsApi = {
  /** Candidate-scoped, paginated list of the caller's own applications. */
  list: async (page = 0, size = 20): Promise<PagedModel<ApplicationResponse>> =>
    request<PagedModel<ApplicationResponse>>(
      `/api/v1/applications${query({ page, size })}`
    ),

  /** Recruiter/ADMIN: applications submitted to one job the caller may see. */
  listByJob: async (jobId: string, page = 0, size = 20): Promise<PagedModel<ApplicationResponse>> =>
    request<PagedModel<ApplicationResponse>>(
      `/api/v1/applications/by-job/${jobId}${query({ page, size })}`
    ),

  create: async (data: ApplicationCreateRequest): Promise<ApplicationResponse> =>
    request<ApplicationResponse>('/api/v1/applications', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),

  get: async (applicationId: string): Promise<ApplicationResponse> =>
    request<ApplicationResponse>(`/api/v1/applications/${applicationId}`),

  withdraw: async (applicationId: string): Promise<ApplicationResponse> =>
    request<ApplicationResponse>(`/api/v1/applications/${applicationId}/withdraw`, {
      method: 'POST',
    }),

  /** Recruiter/ADMIN only. Accepts UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED. */
  updateStatus: async (
    applicationId: string,
    data: ApplicationStatusUpdateRequest
  ): Promise<ApplicationResponse> =>
    request<ApplicationResponse>(`/api/v1/applications/${applicationId}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),
};

/* ======================================================== Documents API */

export type DocumentType = 'RESUME' | 'COVER_LETTER' | 'PORTFOLIO' | 'CERTIFICATE' | 'OTHER';

export interface DocumentResponse {
  documentId: string;
  candidateId: string;
  documentType: DocumentType;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentUploadRequest {
  documentType: string;
  file: File;
}

export const documentsApi = {
  list: async (page = 0, size = 20): Promise<Page<DocumentResponse>> =>
    request<Page<DocumentResponse>>(`/api/v1/documents${query({ page, size })}`),

  listByType: async (documentType: string): Promise<DocumentResponse[]> =>
    request<DocumentResponse[]>(`/api/v1/documents/type/${encodeURIComponent(documentType)}`),

  get: async (documentId: string): Promise<DocumentResponse> =>
    request<DocumentResponse>(`/api/v1/documents/${documentId}`),

  getContent: async (documentId: string): Promise<Blob> =>
    request<Blob>(`/api/v1/documents/${documentId}/content`),

  upload: async (data: DocumentUploadRequest): Promise<DocumentResponse> => {
    const formData = new FormData();
    // The service requires exactly these two part names.
    formData.append(
      'metadata',
      new Blob([JSON.stringify({ documentType: data.documentType })], {
        type: 'application/json',
      })
    );
    formData.append('file', data.file);

    // Content-Type is intentionally unset so the browser supplies the boundary.
    return request<DocumentResponse>('/api/v1/documents', {
      method: 'POST',
      body: formData,
    });
  },

  delete: async (documentId: string): Promise<void> =>
    request<void>(`/api/v1/documents/${documentId}`, { method: 'DELETE' }),
};

/* ==================================================== Notifications API */

export interface NotificationResponse {
  notificationId: string;
  candidateId: string;
  applicationId: string;
  eventStatus: string;
  recipientEmail: string;
  subject: string;
  message: string;
  deliveryStatus: 'PENDING' | 'SENT' | 'FAILED';
  createdAt: string;
  sentAt: string | null;
  errorMessage: string | null;
}

export const notificationsApi = {
  list: async (page = 0, size = 20): Promise<HateoasPagedModel<NotificationResponse>> =>
    request<HateoasPagedModel<NotificationResponse>>(
      `/api/v1/notifications${query({ page, size })}`
    ),

  get: async (notificationId: string): Promise<NotificationResponse> =>
    request<NotificationResponse>(`/api/v1/notifications/${notificationId}`),
};

/* ===================================================== Recruitments API */

export type InterviewStatus = 'NOT_SCHEDULED' | 'SCHEDULED' | 'COMPLETED' | 'NO_SHOW';
export type FinalStatus = 'PENDING' | 'HIRED' | 'REJECTED';

export interface RecruitmentResponse {
  recruitmentId: string;
  applicationId: string;
  jobId: string;
  candidateId: string;
  interviewStatus: InterviewStatus;
  interviewDate: string | null;
  interviewStartTime: string | null;
  interviewer: string | null;
  interviewLocation: string | null;
  finalStatus: FinalStatus;
  /** Not returned by the service; kept optional for defensive rendering. */
  createdAt?: string;
  updatedAt?: string;
}

export interface InterviewStatusUpdateRequest {
  status: string;
}

export interface InterviewScheduleRequest {
  interviewDate: string;
  interviewStartTime: string;
  interviewer: string;
  interviewLocation: string;
}

export interface FinalStatusUpdateRequest {
  status: string;
}

export const recruitmentsApi = {
  /** Shortlist an application: starts both lifecycles at their initial state. */
  create: async (applicationId: string): Promise<RecruitmentResponse> =>
    request<RecruitmentResponse>('/api/v1/recruitments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ applicationId }),
    }),

  list: async (page = 0, size = 20): Promise<PagedModel<RecruitmentResponse>> =>
    request<PagedModel<RecruitmentResponse>>(
      `/api/v1/recruitments${query({ page, size })}`
    ),

  get: async (recruitmentId: string): Promise<RecruitmentResponse> =>
    request<RecruitmentResponse>(`/api/v1/recruitments/${recruitmentId}`),

  /** NOT_SCHEDULED -> SCHEDULED | SCHEDULED -> COMPLETED | SCHEDULED -> NO_SHOW */
  updateInterviewStatus: async (
    recruitmentId: string,
    data: InterviewStatusUpdateRequest
  ): Promise<RecruitmentResponse> =>
    request<RecruitmentResponse>(
      `/api/v1/recruitments/${recruitmentId}/interview-status`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }
    ),

  /** Records date, start time, interviewer and location for the interview. */
  schedule: async (
    recruitmentId: string,
    data: InterviewScheduleRequest
  ): Promise<RecruitmentResponse> =>
    request<RecruitmentResponse>(`/api/v1/recruitments/${recruitmentId}/schedule`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),

  /** PENDING -> HIRED | PENDING -> REJECTED. Both terminal. */
  updateFinalStatus: async (
    recruitmentId: string,
    data: FinalStatusUpdateRequest
  ): Promise<RecruitmentResponse> =>
    request<RecruitmentResponse>(
      `/api/v1/recruitments/${recruitmentId}/final-status`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }
    ),
};
