// API Client for SmartHire Frontend
// Communicates with API Gateway at http://localhost:8080

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  correlationId: string;
}

export interface PagedModel<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

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

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let error: ApiError;
    try {
      error = await response.json();
    } catch {
      error = {
        type: 'about:blank',
        title: response.statusText,
        status: response.status,
        detail: 'An unexpected error occurred',
        instance: response.url,
        correlationId: response.headers.get('X-Correlation-ID') || '',
      };
    }
    throw new ApiErrorResponse(error);
  }
  
  if (response.status === 204) {
    return undefined as T;
  }
  
  const contentType = response.headers.get('content-type');
  if (contentType?.includes('application/json')) {
    return response.json();
  }
  if (contentType?.startsWith('application/') || contentType?.startsWith('image/') || contentType?.startsWith('text/')) {
    return response.blob() as T;
  }
  return response.text() as T;
}

export class ApiErrorResponse extends Error {
  constructor(public readonly error: ApiError) {
    super(error.detail);
    this.name = 'ApiErrorResponse';
  }
}

async function fetchWithAuth<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const accessToken = getAccessToken();
  
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };
  
  if (accessToken) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${accessToken}`;
  }
  
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: 'include', // Important for refresh cookie
  });
  
  return handleResponse<T>(response);
}

async function fetchMultipart<T>(
  path: string,
  formData: FormData,
  options: RequestInit = {}
): Promise<T> {
  const accessToken = getAccessToken();
  
  const headers: HeadersInit = {
    ...options.headers,
  };
  
  if (accessToken) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${accessToken}`;
  }
  
  // Don't set Content-Type for FormData - browser sets it with boundary
  delete (headers as Record<string, string>)['Content-Type'];
  
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method: 'POST',
    headers,
    body: formData,
    credentials: 'include',
  });
  
  return handleResponse<T>(response);
}

async function fetchBlob(path: string): Promise<Blob> {
  const accessToken = getAccessToken();
  
  const headers: HeadersInit = {};
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }
  
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers,
    credentials: 'include',
  });
  
  if (!response.ok) {
    let error: ApiError;
    try {
      error = await response.json();
    } catch {
      error = {
        type: 'about:blank',
        title: response.statusText,
        status: response.status,
        detail: 'Failed to download document',
        instance: response.url,
        correlationId: response.headers.get('X-Correlation-ID') || '',
      };
    }
    throw new ApiErrorResponse(error);
  }
  
  return response.blob();
}

// Token management - in-memory only, never localStorage
let accessToken: string | null = null;
let accessTokenExpiresAt: number | 0 = 0;

export function getAccessToken(): string | null {
  if (accessToken && Date.now() < accessTokenExpiresAt - 30000) { // 30s buffer
    return accessToken;
  }
  return null;
}

export function setAccessToken(token: string, expiresAt: string | number): void {
  accessToken = token;
  accessTokenExpiresAt = typeof expiresAt === 'string' ? new Date(expiresAt).getTime() : expiresAt;
}

export function clearAccessToken(): void {
  accessToken = null;
  accessTokenExpiresAt = 0;
}

export function isTokenExpired(): boolean {
  return Date.now() >= accessTokenExpiresAt;
}

// Auth API
export const authApi = {
  login: async (email: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
      credentials: 'include',
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new ApiErrorResponse(error);
    }
    
    const data = await response.json();
    // accessToken is in response body, refresh token is in Set-Cookie header
    setAccessToken(data.accessToken, data.accessTokenExpiresAt);
    return data;
  },

  register: async (email: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
      credentials: 'include',
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new ApiErrorResponse(error);
    }
    
    return response.json(); // { userId }
  },

  logout: async () => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/logout`, {
      method: 'POST',
      credentials: 'include',
    });
    
    clearAccessToken();
    
    if (!response.ok) {
      const error = await response.json();
      throw new ApiErrorResponse(error);
    }
    
    return response.json();
  },

  refresh: async () => {
    const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    });
    
    if (!response.ok) {
      clearAccessToken();
      const error = await response.json();
      throw new ApiErrorResponse(error);
    }
    
    const data = await response.json();
    setAccessToken(data.accessToken, data.accessTokenExpiresAt);
    return data;
  },

  me: async () => {
    return fetchWithAuth<MeResponse>('/api/v1/auth/me');
  },
};

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

// Jobs API
export interface JobResponse {
  jobId: string;
  companyName: string;
  role: string;
  location: string;
  status: string;
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
  page?: number;
  size?: number;
}

export const jobsApi = {
  list: async (params: JobListParams = {}) => {
    const searchParams = new URLSearchParams();
    if (params.q) searchParams.set('q', params.q);
    if (params.location) searchParams.set('location', params.location);
    if (params.status) searchParams.set('status', params.status);
    if (params.page !== undefined) searchParams.set('page', params.page.toString());
    if (params.size !== undefined) searchParams.set('size', params.size.toString());
    
    return fetchWithAuth<PagedModel<JobResponse>>(`/api/v1/jobs?${searchParams.toString()}`);
  },

  get: async (jobId: string) => {
    return fetchWithAuth<JobResponse>(`/api/v1/jobs/${jobId}`);
  },

  create: async (data: JobCreateRequest) => {
    return fetchWithAuth<JobResponse>('/api/v1/jobs', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  update: async (jobId: string, data: JobUpdateRequest) => {
    return fetchWithAuth<JobResponse>(`/api/v1/jobs/${jobId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  open: async (jobId: string) => {
    return fetchWithAuth<JobResponse>(`/api/v1/jobs/${jobId}/open`, {
      method: 'POST',
    });
  },

  close: async (jobId: string) => {
    return fetchWithAuth<JobResponse>(`/api/v1/jobs/${jobId}/close`, {
      method: 'POST',
    });
  },

  reopen: async (jobId: string) => {
    return fetchWithAuth<JobResponse>(`/api/v1/jobs/${jobId}/reopen`, {
      method: 'POST',
    });
  },
};

// Applications API
export interface ApplicationResponse {
  applicationId: string;
  jobId: string;
  candidateId: string;
  resumeLink: string;
  status: string;
}

export interface ApplicationCreateRequest {
  jobId: string;
  resumeLink: string;
}

export interface ApplicationStatusUpdateRequest {
  status: string;
}

export const applicationsApi = {
  create: async (data: ApplicationCreateRequest) => {
    return fetchWithAuth<ApplicationResponse>('/api/v1/applications', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  get: async (applicationId: string) => {
    return fetchWithAuth<ApplicationResponse>(`/api/v1/applications/${applicationId}`);
  },

  withdraw: async (applicationId: string) => {
    return fetchWithAuth<ApplicationResponse>(`/api/v1/applications/${applicationId}/withdraw`, {
      method: 'POST',
    });
  },

  updateStatus: async (applicationId: string, data: ApplicationStatusUpdateRequest) => {
    return fetchWithAuth<ApplicationResponse>(`/api/v1/applications/${applicationId}/status`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  },
};

// Documents API
export interface DocumentResponse {
  documentId: string;
  candidateId: string;
  documentType: string;
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
  list: async (page = 0, size = 20) => {
    return fetchWithAuth<Page<DocumentResponse>>(`/api/v1/documents?page=${page}&size=${size}`);
  },

  listByType: async (documentType: string) => {
    return fetchWithAuth<DocumentResponse[]>(`/api/v1/documents/type/${documentType}`);
  },

  get: async (documentId: string) => {
    return fetchWithAuth<DocumentResponse>(`/api/v1/documents/${documentId}`);
  },

  getContent: async (documentId: string): Promise<Blob> => {
    return fetchBlob(`/api/v1/documents/${documentId}/content`);
  },

  upload: async (data: DocumentUploadRequest) => {
    const formData = new FormData();
    formData.append('metadata', new Blob([JSON.stringify({ documentType: data.documentType })], { type: 'application/json' }));
    formData.append('file', data.file);
    return fetchMultipart<DocumentResponse>('/api/v1/documents', formData);
  },

  delete: async (documentId: string) => {
    return fetchWithAuth<void>(`/api/v1/documents/${documentId}`, {
      method: 'DELETE',
    });
  },
};

// Notifications API
export interface NotificationResponse {
  notificationId: string;
  candidateId: string;
  applicationId: string;
  eventStatus: string;
  recipientEmail: string;
  subject: string;
  message: string;
  deliveryStatus: string;
  createdAt: string;
  sentAt: string | null;
  errorMessage: string | null;
}

export interface HateoasPagedModel<T> {
  _embedded: { items: T[] };
  _links: {
    first: { href: string };
    last: { href: string };
    next?: { href: string };
    prev?: { href: string };
    self: { href: string };
  };
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
}

export const notificationsApi = {
  list: async (page = 0, size = 20) => {
    return fetchWithAuth<HateoasPagedModel<NotificationResponse>>(`/api/v1/notifications?page=${page}&size=${size}`);
  },

  get: async (notificationId: string) => {
    return fetchWithAuth<NotificationResponse>(`/api/v1/notifications/${notificationId}`);
  },
};

// Recruitments API
export interface RecruitmentResponse {
  recruitmentId: string;
  applicationId: string;
  jobId: string;
  candidateId: string;
  interviewStatus: string;
  finalStatus: string;
  createdAt: string;
  updatedAt: string;
}

export interface InterviewStatusUpdateRequest {
  interviewStatus: string;
}

export interface FinalStatusUpdateRequest {
  finalStatus: string;
}

export const recruitmentsApi = {
  list: async (page = 0, size = 20) => {
    return fetchWithAuth<PagedModel<RecruitmentResponse>>(`/api/v1/recruitments?page=${page}&size=${size}`);
  },

  get: async (recruitmentId: string) => {
    return fetchWithAuth<RecruitmentResponse>(`/api/v1/recruitments/${recruitmentId}`);
  },

  updateInterviewStatus: async (recruitmentId: string, data: InterviewStatusUpdateRequest) => {
    return fetchWithAuth<RecruitmentResponse>(`/api/v1/recruitments/${recruitmentId}/interview-status`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  },

  updateFinalStatus: async (recruitmentId: string, data: FinalStatusUpdateRequest) => {
    return fetchWithAuth<RecruitmentResponse>(`/api/v1/recruitments/${recruitmentId}/final-status`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  },
};