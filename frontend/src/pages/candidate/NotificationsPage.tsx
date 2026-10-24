import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { normaliseHateoas, notificationsApi } from '../../api/client';
import type { NotificationResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonList } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';

const PAGE_SIZE = 10;

/**
 * The candidate's notification inbox.
 *
 * The notifications endpoint returns a HATEOAS `PagedModel` whose `page` block
 * omits `totalPages` and whose items live under `_embedded.notificationResponseList`;
 * `normaliseHateoas` is the single place that shape is understood.
 */
export function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await notificationsApi.list(page, PAGE_SIZE);
      const normalised = normaliseHateoas(result);
      setNotifications(normalised.items);
      setTotalElements(normalised.totalElements);
      setTotalPages(normalised.totalPages);
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load your notifications."));
      setNotifications([]);
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title="Notifications"
        description="Application updates the hiring team has sent you."
      />

      {error && (
        <Alert
          tone="danger"
          title="Notifications unavailable"
          action={<Button size="sm" variant="secondary" onClick={() => void load()}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      {isLoading ? (
        <SkeletonList label="Loading notifications" />
      ) : notifications.length === 0 ? (
        <EmptyState
          icon="notifications_none"
          title="No notifications yet"
          message="You will hear from us here when your application status changes."
        />
      ) : (
        <div className="space-y-4">
          <div className="grid gap-4">
            {notifications.map(notification => (
              <Link
                key={notification.notificationId}
                to={`/candidate/notifications/${notification.notificationId}`}
                className="card p-5 block transition-shadow hover:shadow-hover group"
              >
                <div className="mb-2 flex flex-wrap items-start justify-between gap-2">
                  <StatusBadge status={notification.eventStatus} size="sm" />
                  <span className="font-caption text-caption text-c-text-subtle">
                    {formatDate(notification.createdAt)}
                  </span>
                </div>
                <h2 className="font-headline-sm text-headline-sm text-on-surface break-words group-hover:text-c-primary-700 transition-colors">
                  {notification.subject}
                </h2>
                <p className="mt-1 font-body-md text-body-md text-c-text-muted line-clamp-2">
                  {notification.message}
                </p>
                <p className="mt-3 font-caption text-caption text-c-text-subtle">
                  Delivery: {notification.deliveryStatus}
                </p>
              </Link>
            ))}
          </div>

          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            size={PAGE_SIZE}
            onPageChange={setPage}
          />
        </div>
      )}
    </div>
  );
}
