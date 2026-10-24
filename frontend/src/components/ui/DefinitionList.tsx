import type { ReactNode } from 'react';

export interface DefinitionListProps {
  items: { label: string; value: string | ReactNode }[];
  className?: string;
  /** Number of columns at the widest breakpoint. Sidebar cards usually want one. */
  columns?: 1 | 2 | 3;
}

/**
 * Definition list for key-value pairs, used for showing structured data.
 */
export function DefinitionList({ items, className = '', columns = 3 }: DefinitionListProps) {
  const columnClass =
    columns === 1 ? '' : columns === 2 ? 'sm:grid-cols-2' : 'sm:grid-cols-2 lg:grid-cols-3';

  return (
    <dl className={`grid gap-4 ${columnClass} ${className}`}>
      {items.map((item, index) => (
        <div key={item.label || index} className="flex flex-col gap-1">
          <dt className="font-caption text-caption text-c-text-subtle">{item.label}</dt>
          <dd className="font-body-md text-body-md text-on-surface break-words">
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}