-- V1__notifications.sql
-- Notification Service initial schema

CREATE SCHEMA IF NOT EXISTS notification_svc;

CREATE TABLE notification_svc.notifications (
    notification_id         UUID PRIMARY KEY,
    candidate_id            UUID NOT NULL,
    application_id          UUID NOT NULL,
    event_status            VARCHAR(50) NOT NULL,
    recipient_email         VARCHAR(255) NOT NULL,
    subject                 VARCHAR(255) NOT NULL,
    message                 TEXT NOT NULL,
    delivery_status         VARCHAR(50) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at                 TIMESTAMP WITH TIME ZONE,
    error_message           TEXT
);

CREATE INDEX idx_notifications_candidate_id ON notification_svc.notifications(candidate_id);
CREATE INDEX idx_notifications_application_id ON notification_svc.notifications(application_id);
CREATE INDEX idx_notifications_delivery_status ON notification_svc.notifications(delivery_status);
CREATE INDEX idx_notifications_created_at ON notification_svc.notifications(created_at);

COMMENT ON TABLE notification_svc.notifications IS 'Notification metadata for candidate application status changes';
COMMENT ON COLUMN notification_svc.notifications.notification_id IS 'Primary key, UUID';
COMMENT ON COLUMN notification_svc.notifications.candidate_id IS 'Candidate who owns this notification (from JWT sub)';
COMMENT ON COLUMN notification_svc.notifications.application_id IS 'Application that triggered the notification';
COMMENT ON COLUMN notification_svc.notifications.event_status IS 'Application status that triggered the notification (UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED)';
COMMENT ON COLUMN notification_svc.notifications.recipient_email IS 'Email address of the recipient';
COMMENT ON COLUMN notification_svc.notifications.subject IS 'Email subject';
COMMENT ON COLUMN notification_svc.notifications.message IS 'Email body message';
COMMENT ON COLUMN notification_svc.notifications.delivery_status IS 'Delivery status (PENDING, SENT, FAILED)';
COMMENT ON COLUMN notification_svc.notifications.created_at IS 'Creation timestamp';
COMMENT ON COLUMN notification_svc.notifications.sent_at IS 'Timestamp when notification was sent';
COMMENT ON COLUMN notification_svc.notifications.error_message IS 'Error message if delivery failed';