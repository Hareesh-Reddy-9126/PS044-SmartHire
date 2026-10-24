import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';
import { EmptyState } from '../../components/ui/EmptyState';
import { SkeletonList } from '../../components/ui/Skeleton';

/**
 * Shared building blocks for the candidate workspace.
 *
 * These live in a module of their own so that removing a page cannot silently
 * remove a primitive another page still imports.
 */

/** Blocking load state for a candidate list or detail screen. */
export function LoadingPanel({ label = 'Loading' }: { label?: string }) {
  return <SkeletonList label={label} />;
}

/** Retry affordance for a failed candidate request. */
export function ErrorPanel({
  message = "We couldn't load this information.",
  onRetry,
  backTo = '/candidate/applications',
  backLabel = 'My applications',
}: {
  message?: string;
  onRetry: () => void;
  backTo?: string;
  backLabel?: string;
}) {
  return (
    <div className="mx-auto max-w-xl py-14 text-center">
      <Alert tone="danger" title="Something went wrong">
        {message}
      </Alert>
      <div className="mt-6 flex flex-wrap justify-center gap-3">
        <Button onClick={onRetry}>Try again</Button>
        <Link to={backTo} className="btn btn-secondary btn-md">
          {backLabel}
        </Link>
      </div>
    </div>
  );
}

/** Empty state for a candidate list that legitimately has no rows. */
export function EmptyPanel({
  title,
  message,
  action,
}: {
  title: string;
  message: string;
  action?: ReactNode;
}) {
  return <EmptyState title={title} message={message} action={action} />;
}
