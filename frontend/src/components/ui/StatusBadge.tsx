export interface StatusBadgeProps {
  status: string;
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * The single renderer for every status in the product.
 *
 * The raw enum value is shown rather than a prettified label: the wire contract
 * and the UI stay in one-to-one correspondence, which makes a status easy to
 * match against an API response or a log line. Styling is supplied by the
 * stylesheet's `::before` dot, so colour is never the only signal.
 */
export function StatusBadge({ status, size = 'md', className = '' }: StatusBadgeProps) {
  const known = isKnownStatus(status);
  const tone = known ? `status-${status.toLowerCase()}` : 'status-pending';

  return (
    <span
      className={`status-badge ${tone} ${size === 'sm' ? 'text-[10px] px-1.5 h-5' : ''} ${className}`}
      title={known ? undefined : `Unrecognised status: ${status}`}
    >
      {status}
    </span>
  );
}

const KNOWN_STATUSES = [
  // Job
  'DRAFT',
  'OPEN',
  'CLOSED',
  // Application
  'APPLIED',
  'UNDER_REVIEW',
  'SHORTLISTED',
  'HIRED',
  'REJECTED',
  'WITHDRAWN',
  // Recruitment — interview lifecycle
  'NOT_SCHEDULED',
  'SCHEDULED',
  'COMPLETED',
  'NO_SHOW',
  // Recruitment — decision lifecycle
  'PENDING',
  // Notification delivery
  'SENT',
  'FAILED',
] as const;

export type StatusType = (typeof KNOWN_STATUSES)[number];

const STATUS_SET = new Set<string>(KNOWN_STATUSES);

export function isKnownStatus(status: string): status is StatusType {
  return STATUS_SET.has(status);
}