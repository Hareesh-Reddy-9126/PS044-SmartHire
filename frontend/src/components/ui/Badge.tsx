import type { ReactNode } from 'react';

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps {
  children: ReactNode;
  tone?: BadgeTone;
  size?: BadgeSize;
  icon?: string;
  withDot?: boolean;
  className?: string;
}

const TONE_CLASSES: Record<BadgeTone, { badge: string; dot: string }> = {
  neutral: {
    badge: 'bg-c-surface-sunken text-c-text-muted border-c-border',
    dot: 'bg-c-text-subtle',
  },
  primary: {
    badge: 'bg-c-primary-50 text-c-primary-700 border-c-primary-200',
    dot: 'bg-c-primary-600',
  },
  success: {
    badge: 'bg-success-50 text-success-700 border-success-200',
    dot: 'bg-success-600',
  },
  warning: {
    badge: 'bg-warning-50 text-warning-800 border-warning-200',
    dot: 'bg-warning-600',
  },
  danger: {
    badge: 'bg-danger-50 text-danger-700 border-danger-200',
    dot: 'bg-danger-600',
  },
  info: {
    badge: 'bg-info-50 text-info-700 border-info-200',
    dot: 'bg-info-600',
  },
};

/**
 * General badge for category tags, counts, and non-status flags.
 * (For domain workflow status, prefer `StatusBadge`).
 */
export function Badge({
  children,
  tone = 'neutral',
  size = 'md',
  icon,
  withDot = false,
  className = '',
}: BadgeProps) {
  const toneStyle = TONE_CLASSES[tone] ?? TONE_CLASSES.neutral;
  const sizeClasses = size === 'sm' ? 'h-5 px-2 text-[11px]' : 'h-6 px-2.5 text-caption';

  return (
    <span
      className={`inline-flex items-center gap-1.5 font-label-md rounded-full border ${sizeClasses} ${toneStyle.badge} ${className}`}
    >
      {withDot && (
        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${toneStyle.dot}`} aria-hidden="true" />
      )}
      {icon && (
        <span className="material-symbols-outlined text-[14px] shrink-0" aria-hidden="true">
          {icon}
        </span>
      )}
      <span className="truncate">{children}</span>
    </span>
  );
}
