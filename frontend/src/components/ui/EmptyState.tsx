import React from 'react';

export interface EmptyStateProps {
  /** Material Symbols ligature name. */
  icon?: string;
  title: string;
  message?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}

/**
 * Shown when a list legitimately has no rows.
 *
 * The copy is supplied per screen — a generic "No data" tells the user nothing
 * about whether the state is expected or whether an action would change it.
 */
export function EmptyState({
  icon = 'inbox',
  title,
  message,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div className={`empty-state ${className}`}>
      <span className="empty-state-icon" aria-hidden="true">
        <span className="material-symbols-outlined text-[24px]">{icon}</span>
      </span>
      <h2 className="empty-state-title">{title}</h2>
      {message && <p className="empty-state-message">{message}</p>}
      {action && <div className="mt-1 flex flex-wrap justify-center gap-3">{action}</div>}
    </div>
  );
}