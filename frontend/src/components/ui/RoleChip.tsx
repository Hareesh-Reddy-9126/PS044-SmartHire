type RoleType = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

interface RoleChipProps {
  role: RoleType;
  size?: 'sm' | 'md';
}

export function RoleChip({ role, size = 'md' }: RoleChipProps) {
  const className = `role-chip role-${role.toLowerCase()} ${size === 'sm' ? 'text-[10px] px-2 py-0.5' : ''}`;

  return (
    <span className={className}>
      {role.charAt(0) + role.slice(1).toLowerCase()}
    </span>
  );
}