import React from 'react';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

export interface AlertProps {
  tone?: AlertTone;
  title?: string;
  children: React.ReactNode;
  /** Rendered bottom-right, e.g. a retry action. */
  action?: React.ReactNode;
  className?: string;
  /** Errors default to assertive so they interrupt; others are polite. */
  live?: 'polite' | 'assertive' | 'off';
}

const TONE_STYLE: Record<AlertTone, { panel: string; icon: string; glyph: string }> = {
  info: {
    panel: 'bg-info-50 border-info-200 text-info-800',
    icon: 'text-info-600',
    glyph: 'info',
  },
  success: {
    panel: 'bg-success-50 border-success-200 text-success-800',
    icon: 'text-success-600',
    glyph: 'check_circle',
  },
  warning: {
    panel: 'bg-warning-50 border-warning-200 text-warning-800',
    icon: 'text-warning-600',
    glyph: 'warning',
  },
  danger: {
    panel: 'bg-danger-50 border-danger-200 text-danger-800',
    icon: 'text-danger-600',
    glyph: 'error',
  },
};

export function Alert({
  tone = 'info',
  title,
  children,
  action,
  className = '',
  live,
}: AlertProps) {
  const role = tone === 'danger' ? 'alert' : 'status';
  const ariaLive = live === 'off' ? 'off' : live ?? (tone === 'danger' ? 'assertive' : 'polite');
  const style = TONE_STYLE[tone];

  return (
    <div
      role={role}
      aria-live={ariaLive}
      className={`flex items-start gap-3 rounded-lg border p-3.5 ${style.panel} ${className}`}
    >
      <span
        className={`material-symbols-outlined mt-px shrink-0 text-[20px] ${style.icon}`}
        aria-hidden="true"
      >
        {style.glyph}
      </span>

      <div className="min-w-0 grow">
        {title && <p className="font-body-strong text-body-strong">{title}</p>}
        <div className={`font-body-md text-body-md ${title ? 'mt-0.5 opacity-90' : ''}`}>
          {children}
        </div>
      </div>

      {action && <div className="shrink-0 self-center">{action}</div>}
    </div>
  );
}

/**
 * GapNotice - renders a notice about a gap/limitation in the UI.
 */
export function GapNotice({
  message = 'This feature is not yet available.',
  className = '',
}: {
  message?: string;
  className?: string;
}) {
  return (
    <div
      className={[
        'flex items-start gap-3 rounded-lg border border-c-border bg-c-surface-sunken p-3 text-c-text-muted',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span className="material-symbols-outlined mt-px shrink-0 text-[20px]" aria-hidden="true">
        info
      </span>
      <div className="min-w-0 grow">
        <p className="font-body-md text-body-md text-c-text-muted">{message}</p>
      </div>
    </div>
  );
}
