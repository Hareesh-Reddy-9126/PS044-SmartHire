import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  authApi,
  clearAccessToken,
  documentsApi,
  endSession,
  normaliseHateoas,
  notificationsApi,
} from '../api/client';

const notification = {
  notificationId: 'notification-1',
  candidateId: 'candidate-1',
  applicationId: 'application-1',
  eventStatus: 'HIRED',
  recipientEmail: 'candidate@example.test',
  subject: "Job offer: You've been hired!",
  message: 'Welcome aboard.',
  deliveryStatus: 'SENT',
  createdAt: '2026-10-24T12:43:14Z',
  sentAt: '2026-10-24T12:43:14Z',
  errorMessage: null,
};

function response(body: unknown, contentType: string): Response {
  return {
    ok: true,
    status: 200,
    headers: {
      get: (name: string) => (name.toLowerCase() === 'content-type' ? contentType : null),
    },
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

describe('api client response parsing', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    clearAccessToken();
  });

  it('parses HATEOAS application/hal+json envelopes instead of returning raw text', async () => {
    const payload = {
      _embedded: { notificationResponseList: [notification] },
      _links: {},
      page: { size: 10, totalElements: 1, totalPages: 1, number: 0 },
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(payload, 'application/hal+json')));

    const result = await notificationsApi.list(0, 10);
    const normalised = normaliseHateoas(result);

    expect(normalised.items).toHaveLength(1);
    expect(normalised.items[0].subject).toBe("Job offer: You've been hired!");
    expect(normalised.totalElements).toBe(1);
  });

  it('returns a Blob for document content served as application/pdf', async () => {
    const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: {
          get: (name: string) =>
            name.toLowerCase() === 'content-type' ? 'application/pdf' : null,
        },
        text: async () => '%PDF-1.4',
        blob: async () => pdf,
      } as unknown as Response)
    );

    const result = await documentsApi.getContent('document-1');

    expect(result).toBeInstanceOf(Blob);
    expect(result.type).toBe('application/pdf');
    expect(result.size).toBe(8);
  });

  it('sends the bearer token on logout so the server revokes the refresh cookie', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        response(
          {
            tokenType: 'Bearer',
            accessToken: 'access-token-1',
            accessTokenExpiresAt: '2099-01-01T00:00:00Z',
          },
          'application/json'
        )
      )
      .mockResolvedValueOnce({
        ok: true,
        status: 204,
        headers: { get: () => null },
        text: async () => '',
      } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await authApi.login('candidate@example.com', 'Secret123!');
    await endSession();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [logoutUrl, logoutInit] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(logoutUrl).toContain('/api/v1/auth/logout');
    expect(new Headers(logoutInit.headers).get('Authorization')).toBe('Bearer access-token-1');
  });
});
