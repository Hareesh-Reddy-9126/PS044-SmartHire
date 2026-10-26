/**
 * Role-aware routing and registration metadata.
 *
 * Both the protected-route redirect and the post-login redirect need to agree on
 * where a role belongs; keeping the table here avoids the two drifting apart.
 */
export const ACCOUNT_ROLES = ['CANDIDATE', 'RECRUITER', 'ADMIN'] as const;
export type AccountRole = (typeof ACCOUNT_ROLES)[number];

export interface RoleMeta {
  value: AccountRole;
  label: string;
  /** Short description used by the shared sign-in / sign-up role selector. */
  hint: string;
  icon: string;
  /** Workspace entry path for this role. */
  home: string;
}

export const ROLE_META: Record<AccountRole, RoleMeta> = {
  CANDIDATE: {
    value: 'CANDIDATE',
    label: 'Candidate',
    hint: 'Apply for roles and follow your progress',
    icon: 'person_search',
    home: '/candidate/applications',
  },
  RECRUITER: {
    value: 'RECRUITER',
    label: 'Recruiter',
    hint: 'Hire for your organization',
    icon: 'badge',
    home: '/recruiter/jobs',
  },
  ADMIN: {
    value: 'ADMIN',
    label: 'Administrator',
    hint: 'Operate and review the platform',
    icon: 'admin_panel_settings',
    home: '/admin/overview',
  },
};

/**
 * Workspace precedence for accounts that hold more than one role. Most
 * privileged first, so an administrator never lands in a candidate view.
 */
const ROLE_HOME: ReadonlyArray<readonly [string, string]> = [
  ['ADMIN', ROLE_META.ADMIN.home],
  ['RECRUITER', ROLE_META.RECRUITER.home],
  ['CANDIDATE', ROLE_META.CANDIDATE.home],
];

/** True when a value is one of the three self-service roles, in canonical upper case. */
export function isAccountRole(value: unknown): value is AccountRole {
  return typeof value === 'string' && (ACCOUNT_ROLES as readonly string[]).includes(value);
}

/**
 * Convert a free-form query value into a role, defaulting to candidate.
 * Unknown or missing values must never widen access; they only choose which
 * portal the visitor intended to open.
 */
export function normaliseRole(value: string | null | undefined): AccountRole {
  if (!value) return 'CANDIDATE';
  const upper = value.trim().toUpperCase();
  return isAccountRole(upper) ? upper : 'CANDIDATE';
}

/** Human-readable label for any role string, including future/unknown values. */
export function roleLabel(role: string | null | undefined): string {
  if (!role) return 'Candidate';
  return ROLE_META[role.toUpperCase() as AccountRole]?.label ?? role;
}

/** The role that owns the caller's primary workspace, when a token carries several. */
export function primaryRole(roles: string[] | undefined): AccountRole {
  if (!roles?.length) return 'CANDIDATE';
  for (const [role] of ROLE_HOME) {
    if (roles.includes(role)) return role as AccountRole;
  }
  return 'CANDIDATE';
}

/** Default landing path for the caller's most privileged role. */
export function roleHome(roles: string[] | undefined): string {
  if (!roles?.length) return '/jobs';
  for (const [role, home] of ROLE_HOME) {
    if (roles.includes(role)) return home;
  }
  return '/jobs';
}

/**
 * Build a sign-in / sign-up URL that preserves the intended portal and an
 * optional same-origin destination.
 */
export function authPath(
  path: '/login' | '/register',
  role: AccountRole,
  next?: string | null
): string {
  const params = new URLSearchParams({ role });
  if (next) params.set('next', next);
  return `${path}?${params.toString()}`;
}

/**
 * Pick the post-login destination.
 *
 * A `next` value is only honoured when it is a same-origin path the caller's
 * roles can actually open. Without this check a stale link such as
 * `/candidate/applications` follows a recruiter through sign-in and lands them
 * on the 403 page.
 */
export function safeNextPath(next: string | null, roles: string[]): string {
  const home = roleHome(roles);
  if (!next || !next.startsWith('/') || next.startsWith('//')) return home;

  // Candidate-only apply screen: not under /candidate, so check it explicitly.
  if (!roles.includes('CANDIDATE') && /^\/jobs\/[^/]+\/apply(?:\/|$)/.test(next)) {
    return home;
  }

  const forbiddenArea = ROLE_HOME.some(([role]) => {
    const prefix = `/${role.toLowerCase()}`;
    return (
      !roles.includes(role) && (next === prefix || next.startsWith(`${prefix}/`))
    );
  });

  return forbiddenArea ? home : next;
}
