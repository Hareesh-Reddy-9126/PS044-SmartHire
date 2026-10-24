/**
 * Loading placeholders. Each one mirrors the height of the content it stands in
 * for so the page does not jump when the real data arrives.
 */

/** A single skeleton block. */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`skeleton h-4 ${className}`} />;
}

/**
 * A single skeleton line.
 */
export function SkeletonLine({ className = '' }: { className?: string }) {
  return <div className={`skeleton h-4 ${className}`} />;
}

/** A stack of card-shaped placeholders for list screens. */
export function SkeletonList({ rows = 5, label = 'Loading list' }: { rows?: number; label?: string }) {
  return (
    <div className="space-y-3" role="status" aria-busy="true" aria-label={label}>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="card p-5 flex items-center justify-between gap-4">
          <div className="grow space-y-2.5">
            <div className="skeleton h-5 w-1/3" />
            <div className="skeleton h-3.5 w-1/2" />
          </div>
          <div className="skeleton h-6 w-20 rounded-full shrink-0" />
        </div>
      ))}
      <span className="sr-only">{label}</span>
    </div>
  );
}

/** A grid of card placeholders for browsing screens. */
export function SkeletonCards({ count = 6, label = 'Loading results' }: { count?: number; label?: string }) {
  return (
    <div
      className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3"
      role="status"
      aria-busy="true"
      aria-label={label}
    >
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="card p-6 space-y-3">
          <div className="skeleton h-6 w-20 rounded-full" />
          <div className="skeleton h-5 w-3/4" />
          <div className="skeleton h-3.5 w-1/2" />
          <div className="skeleton h-3.5 w-1/3" />
        </div>
      ))}
    </div>
  );
}

/** A detail-page placeholder: heading, meta row, and body block. */
export function SkeletonDetail({ label = 'Loading details' }: { label?: string }) {
  return (
    <div className="space-y-6" role="status" aria-busy="true" aria-label={label}>
      <div className="space-y-3">
        <div className="skeleton h-4 w-28" />
        <div className="skeleton h-8 w-2/3" />
        <div className="skeleton h-4 w-1/3" />
      </div>
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="card lg:col-span-2 h-72" />
        <div className="card h-56" />
      </div>
    </div>
  );
}