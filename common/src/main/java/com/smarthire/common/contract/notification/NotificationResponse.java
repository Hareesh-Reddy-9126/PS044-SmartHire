package com.smarthire.common.contract.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Contract DTO for notification creation response.
 *
 * <p>Shared between Notification Service (sender) and Application Service (receiver). This is the
 * cross-service contract - no implementation details leak here.
 */
@Schema(
    name = "NotificationResponse",
    description = "Notification response with delivery status and details")
public record NotificationResponse(
    @Schema(
            name = "notificationId",
            description = "Unique identifier of the notification",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID notificationId,
    @Schema(
            name = "candidateId",
            description = "ID of the candidate this notification is for",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID candidateId,
    @Schema(
            name = "applicationId",
            description = "ID of the related application",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID applicationId,
    @Schema(
            name = "eventStatus",
            description = "Status of the application event that triggered the notification",
            example = "HIRED")
        String eventStatus,
    @Schema(
            name = "recipientEmail",
            description = "Email address of the notification recipient",
            example = "candidate@example.com")
        String recipientEmail,
    @Schema(
            name = "subject",
            description = "Notification subject line",
            example = "Your application has been HIRED")
        String subject,
    @Schema(
            name = "message",
            description = "Notification message body",
            example = "Congratulations! Your application...")
        String message,
    @Schema(
            name = "deliveryStatus",
            description = "Delivery status of the notification",
            example = "SENT")
        DeliveryStatus deliveryStatus,
    @Schema(
            name = "createdAt",
            description = "Timestamp when notification was created",
            example = "2024-01-15T10:30:00Z")
        Instant createdAt,
    @Schema(
            name = "sentAt",
            description = "Timestamp when notification was sent (null if not sent)",
            example = "2024-01-15T10:30:05Z")
        Instant sentAt,
    @Schema(
            name = "errorMessage",
            description = "Error message if delivery failed",
            example = "SMTP connection timeout")
        String errorMessage) {}
