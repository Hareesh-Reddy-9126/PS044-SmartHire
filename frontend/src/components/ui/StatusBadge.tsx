type StatusType = 
  | 'OPEN' | 'DRAFT' | 'CLOSED'
  | 'APPLIED' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'HIRED' | 'REJECTED' | 'WITHDRAWN'
  | 'NOT_SCHEDULED' | 'SCHEDULED' | 'COMPLETED' | 'NO_SHOW'
  | 'PENDING';

interface StatusBadgeProps {
  status: StatusType;
  size?: 'sm' | 'md';
  showDot?: boolean;
}

const statusConfig: Record<StatusType, { label: string }> = {
  OPEN: { label: 'OPEN' },
  DRAFT: { label: 'DRAFT' },
  CLOSED: { label: 'CLOSED' },
  APPLIED: { label: 'APPLIED' },
  UNDER_REVIEW: { label: 'UNDER_REVIEW' },
  SHORTLISTED: { label: 'SHORTLISTED' },
  HIRED: { label: 'HIRED' },
  REJECTED: { label: 'REJECTED' },
  WITHDRAWN: { label: 'WITHDRAWN' },
  NOT_SCHEDULED: { label: 'NOT_SCHEDULED' },
  SCHEDULED: { label: 'SCHEDULED' },
  COMPLETED: { label: 'COMPLETED' },
  NO_SHOW: { label: 'NO_SHOW' },
  PENDING: { label: 'PENDING' },
};

export function StatusBadge({ status, size = 'md', showDot = true }: StatusBadgeProps) {
  const config = statusConfig[status] || { label: status };
  const className = `status-badge status-${status.toLowerCase()} ${size === 'sm' ? 'text-[10px] px-1.5 h-5' : ''}`;

  return (
    <span className={className}>
      {showDot && <span className="w-1.5 h-1.5 rounded-full" aria-hidden="true" />}
      {config.label}
    </span>
  );
}