import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { notificationsApi } from '../../api/client';
import type { NotificationResponse } from '../../api/client';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CopyableId } from '../../components/ui/CopyableId';
import { DefinitionList } from '../../components/ui/DefinitionList';
import { PageHeader } from '../../components/ui/PageHeader';
import { SkeletonDetail } from '../../components/ui/Skeleton';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';

export function NotificationDetailPage() {
  const { notificationId } = useParams<{ notificationId: string }>();
  const [notification, setNotification] = useState<NotificationResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!notificationId) {
      setIsLoading(false);
      setError('No notification was specified.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      setNotification(await notificationsApi.get(notificationId));
    } catch (caught) {
      setError(errorMessage(caught, "We couldn't load this notification."));
    } finally {
      setIsLoading(false);
    }
  }, [notificationId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl py-12">
        <SkeletonDetail />
      </div>
    );
  }

  if (error || !notification) {
    return (
      <div className="mx-auto max-w-xl space-y-6 py-12 text-center">
        <Alert tone="danger" title="Notification unavailable">
          {error ?? "We couldn't find this notification."}
        </Alert>
        <div className="flex justify-center gap-3">
          <Button onClick={() => void load()}>Try again</Button>
          <Link to="/candidate/notifications" className="btn btn-secondary btn-md">
            Back to notifications
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow="Candidate workspace"
        title={notification.subject}
        action={<StatusBadge status={notification.deliveryStatus} />}
      />

      <div className="grid items-start gap-8 lg:grid-cols-3">
        <Card padding="lg" className="lg:col-span-2">
          <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Message</h2>
          <p className="whitespace-pre-wrap font-body-md text-body-md text-c-text">
            {notification.message}
          </p>
          <p className="mt-6 font-caption text-caption text-c-text-subtle">
            Sent to {notification.recipientEmail} · {formatDate(notification.createdAt)}
          </p>
          {notification.errorMessage && (
            <Alert tone="warning" title="Delivery problem" className="mt-4">
              {notification.errorMessage}
            </Alert>
          )}
        </Card>

        <Card padding="lg">
          <h2 className="mb-4 font-headline-sm text-headline-sm text-on-surface">Details</h2>
          <DefinitionList
            items={[
              { label: 'Event', value: <StatusBadge status={notification.eventStatus} size="sm" /> },
              {
                label: 'Notification ID',
                value: <CopyableId value={notification.notificationId} />,
              },
              { label: 'Application', value: <CopyableId value={notification.applicationId} /> },
              { label: 'Created', value: formatDate(notification.createdAt) },
              {
                label: 'Delivered',
                value: notification.sentAt ? formatDate(notification.sentAt) : 'Not yet delivered',
              },
            ]}
          />

          <Link
            to={`/candidate/applications/${notification.applicationId}`}
            className="btn btn-secondary btn-sm mt-5"
          >
            View application
          </Link>
        </Card>
      </div>

      <Link to="/candidate/notifications" className="btn btn-ghost btn-md">
        Back to notifications
      </Link>
    </div>
  );
}
