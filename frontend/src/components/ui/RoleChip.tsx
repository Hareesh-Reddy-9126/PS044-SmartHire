export type RoleType = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

const ROLE_LABEL: Record<RoleType, string> = {
  CANDIDATE: 'Candidate',
  RECRUITER: 'Recruiter',
  ADMIN: 'Admin',
};

const ROLE_CLASS: Record<RoleType, string> = {
  CANDIDATE: 'role-candidate',
  RECRUITER: 'role-recruiter',
  ADMIN: 'role-admin',
};

/**
 * Displays the caller's role so an authorization refusal is always explicable.
 * Unknown roles fall back to a neutral chip instead of rendering nothing.
 */
export function RoleChip({ role, size = 'md' }: { role: string; size?: 'sm' | 'md' }) {
  const known = role in ROLE_LABEL;
  const label = known ? ROLE_LABEL[role as RoleType] : role;
  const tone = known ? ROLE_CLASS[role as RoleType] : 'bg-c-surface-sunken text-c-text-muted';

  return (
    <span
      className={`role-chip ${tone} ${size === 'sm' ? 'text-[10px] px-2 py-0.5' : ''}`}
      title={`Role: ${role}`}
    >
      {label}
    </span>
  );
}
