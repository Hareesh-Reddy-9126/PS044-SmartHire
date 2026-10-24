/**
 * Role-aware landing paths.
 *
 * Both the protected-route redirect and the post-login redirect need to agree on
 * where a role belongs; keeping the table here avoids the two drifting apart.
 */
const ROLE_HOME: ReadonlyArray<readonly [string, string]> = [
  ['ADMIN', '/admin/overview'],
  ['RECRUITER', '/recruiter/jobs'],
  ['CANDIDATE', '/candidate/applications'],
];

/** Default landing path for the caller's most privileged role. */
export function roleHome(roles: string[] | undefined): string {
  if (!roles?.length) return '/jobs';
  for (const [role, home] of ROLE_HOME) {
    if (roles.includes(role)) return home;
  }
  return '/jobs';
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
