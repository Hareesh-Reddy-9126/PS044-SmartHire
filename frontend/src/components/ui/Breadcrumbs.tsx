import React from 'react';
import { Link } from 'react-router-dom';

export interface BreadcrumbsItem {
  label: string;
  href?: string;
  current?: boolean;
}

export interface BreadcrumbsProps {
  items: BreadcrumbsItem[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
  if (!items || items.length === 0) return null;

  return (
    <nav
      aria-label="Breadcrumb"
      className="flex flex-wrap items-center gap-1.5 text-c-text-subtle font-caption text-caption"
    >
      {items.map((item, index) => {
        const isCurrent = item.current ?? index === items.length - 1;
        return (
          <React.Fragment key={item.label || index}>
            {item.href && !isCurrent ? (
              <Link
                to={item.href}
                className="text-c-text-muted transition-colors duration-fast hover:text-c-primary-700"
              >
                {item.label}
              </Link>
            ) : (
              <span
                className={isCurrent ? 'text-c-primary-800' : 'text-c-text'}
                aria-current={isCurrent ? 'page' : undefined}
              >
                {item.label}
              </span>
            )}
            {index < items.length - 1 && (
              <span className="text-c-border-strong" aria-hidden="true">
                /
              </span>
            )}
          </React.Fragment>
        );
      })}
    </nav>
  );
}
