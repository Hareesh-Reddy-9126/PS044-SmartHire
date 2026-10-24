import React from 'react';

export interface CardProps {
  children: React.ReactNode;
  className?: string;
  /** Applies padding directly to the card. Do not combine with Card* sub-parts. */
  padding?: 'none' | 'sm' | 'md' | 'lg';
  as?: 'div' | 'section' | 'article' | 'li';
}

const PADDING_CLASS = {
  none: '',
  sm: 'p-3',
  md: 'p-5',
  lg: 'p-6',
};

/**
 * Surface container. Either put content straight in with `padding`, or compose
 * with `CardHeader` / `CardBody` / `CardFooter`, which carry their own padding
 * and dividers — mixing the two double-pads the content.
 */
export function Card({ children, className = '', padding = 'md', as: Tag = 'div' }: CardProps) {
  return <Tag className={`card ${PADDING_CLASS[padding]} ${className}`}>{children}</Tag>;
}

export function CardHeader({
  children,
  className = '',
  action,
}: {
  children: React.ReactNode;
  className?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className={`card-header flex items-start justify-between gap-4 ${className}`}>
      <div className="min-w-0">{children}</div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function CardBody({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <div className={`card-body ${className}`}>{children}</div>;
}

export function CardFooter({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <div className={`card-footer ${className}`}>{children}</div>;
}