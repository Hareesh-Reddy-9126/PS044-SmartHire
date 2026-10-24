import { describe, expect, it } from 'vitest';
import { roleHome, safeNextPath } from '../utils/roles';

describe('role-aware routing', () => {
  it('maps each role to its workspace', () => {
    expect(roleHome(['CANDIDATE'])).toBe('/candidate/applications');
    expect(roleHome(['RECRUITER'])).toBe('/recruiter/jobs');
    expect(roleHome(['ADMIN'])).toBe('/admin/overview');
    expect(roleHome(['CANDIDATE', 'RECRUITER'])).toBe('/recruiter/jobs');
    expect(roleHome(undefined)).toBe('/jobs');
  });

  it('keeps a next path that belongs to the signed-in role', () => {
    expect(safeNextPath('/candidate/applications', ['CANDIDATE'])).toBe(
      '/candidate/applications'
    );
    expect(safeNextPath('/recruiter/jobs/abc?tab=applicants', ['RECRUITER'])).toBe(
      '/recruiter/jobs/abc?tab=applicants'
    );
    expect(safeNextPath('/jobs', ['CANDIDATE'])).toBe('/jobs');
    expect(safeNextPath('/jobs/abc/apply', ['CANDIDATE'])).toBe('/jobs/abc/apply');
  });

  it('drops a next path aimed at another role instead of producing a 403', () => {
    expect(safeNextPath('/candidate/applications', ['RECRUITER'])).toBe(
      '/recruiter/jobs'
    );
    expect(safeNextPath('/admin/overview', ['RECRUITER'])).toBe('/recruiter/jobs');
    expect(safeNextPath('/jobs/abc/apply', ['RECRUITER'])).toBe('/recruiter/jobs');
    expect(safeNextPath('/candidate/profile', ['ADMIN'])).toBe('/admin/overview');
  });

  it('rejects external and malformed destinations', () => {
    expect(safeNextPath('https://example.com/phish', ['CANDIDATE'])).toBe(
      '/candidate/applications'
    );
    expect(safeNextPath('//example.com/phish', ['CANDIDATE'])).toBe(
      '/candidate/applications'
    );
    expect(safeNextPath('candidate/applications', ['CANDIDATE'])).toBe(
      '/candidate/applications'
    );
    expect(safeNextPath(null, ['CANDIDATE'])).toBe('/candidate/applications');
  });
});
