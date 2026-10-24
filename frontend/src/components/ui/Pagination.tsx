import { useState } from 'react';
import { Select } from './Select';

export interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements?: number;
  size?: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
  /** The server clamps to 100; never offer more than that. */
  sizeOptions?: number[];
  className?: string;
}

/**
 * Server-driven paging control.
 *
 * `totalPages` comes from the response envelope, so the control never guesses
 * how many pages exist. The page count is clamped to 1 so an empty result set
 * reads "Page 1 of 1" rather than "Page 1 of 0".
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  sizeOptions = [10, 20, 50, 100],
  className = '',
}: PaginationProps) {
  const safeTotal = Math.max(totalPages, 1);
  const [announcement, setAnnouncement] = useState('');

  const go = (next: number) => {
    const clamped = Math.min(Math.max(next, 0), safeTotal - 1);
    if (clamped === page) return;
    onPageChange(clamped);
    setAnnouncement(`Page ${clamped + 1} of ${safeTotal}`);
  };

  const first = page * (size ?? 0) + 1;
  const last = size ? Math.min((page + 1) * size, totalElements ?? 0) : 0;
  const showRange = typeof totalElements === 'number' && typeof size === 'number' && totalElements > 0;

  return (
    <nav className={`pagination ${className}`} aria-label="Pagination">
      <div className="flex items-center gap-3 order-2 sm:order-1">
        {showRange && (
          <span className="tabular">
            {first}–{last} of {totalElements}
          </span>
        )}

        {onSizeChange && (
          <Select
            aria-label="Rows per page"
            value={String(size ?? '')}
            onChange={event => onSizeChange(Number(event.target.value))}
            options={sizeOptions.map(option => ({
              value: String(option),
              label: `${option} / page`,
            }))}
            compact
          />
        )}
      </div>

      <div className="flex items-center gap-1 order-1 sm:order-2">
        <button
          type="button"
          className="pagination-btn"
          onClick={() => go(0)}
          disabled={page <= 0}
          aria-label="First page"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            first_page
          </span>
        </button>
        <button
          type="button"
          className="pagination-btn"
          onClick={() => go(page - 1)}
          disabled={page <= 0}
          aria-label="Previous page"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            chevron_left
          </span>
        </button>

        <span className="px-2 text-on-surface font-body-strong text-body-strong tabular select-none">
          Page {page + 1} of {safeTotal}
        </span>

        <button
          type="button"
          className="pagination-btn"
          onClick={() => go(page + 1)}
          disabled={page >= safeTotal - 1}
          aria-label="Next page"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            chevron_right
          </span>
        </button>
        <button
          type="button"
          className="pagination-btn"
          onClick={() => go(safeTotal - 1)}
          disabled={page >= safeTotal - 1}
          aria-label="Last page"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            last_page
          </span>
        </button>
      </div>

      <span className="sr-only" role="status" aria-live="polite">
        {announcement}
      </span>
    </nav>
  );
}
