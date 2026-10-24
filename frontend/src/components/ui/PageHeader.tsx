import React from 'react';

export interface PageHeaderProps {
  /** Small label above the title, e.g. "Candidate workspace". */
  eyebrow?: string;
  title: string;
  description?: React.ReactNode;
  /** Primary action, right-aligned on desktop and full-width below the title on mobile. */
  action?: React.ReactNode;
  /** Rendered under the divider, e.g. a filter bar. */
  children?: React.ReactNode;
}

/**
 * The standard header for every application screen.
 *
 * One visible `<h1>` per page, with the primary action placed consistently so a
 * user always knows where the main next step lives. The serif title belongs to
 * the "decision record" identity; the surrounding interface stays quiet.
 */
export function PageHeader({ eyebrow, title, description, action, children }: PageHeaderProps) {
  return (
    <div>
      <div className="page-header">
        <div className="min-w-0">
          {eyebrow && <p className="page-eyebrow">{eyebrow}</p>}
          <h1 className="page-title break-words">{title}</h1>
          {description && <p className="page-description">{description}</p>}
        </div>

        {action && (
          <div className="flex w-full shrink-0 flex-wrap items-center gap-2 md:w-auto md:justify-end">
            {action}
          </div>
        )}
      </div>

      {children}
    </div>
  );
}
