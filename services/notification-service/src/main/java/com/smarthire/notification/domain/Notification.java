package com.smarthire.notification.domain;

import com.smarthire.common.contract.notification.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Notification aggregate. Represents a notification to be sent to a candidate when their
 * application status changes. Persisted to {@code notification_svc.notifications}.
 *
 * <p>The notification lifecycle is:
 *
 * <pre>
 *   PENDING
 *      |
 *      v
 *   SENT/FAILED
 * </pre>
 *
 * <p>Transitions are managed by {@link NotificationService}: a notification starts PENDING, is
 * attempted via {@link NotificationSender}, and ends either SENT or FAILED. Once terminal
 * (SENT/FAILED), no further transitions are allowed.
 */
@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @Column(name = "notification_id", nullable = false, updatable = false)
  private UUID notificationId;

  @Column(name = "candidate_id", nullable = false, updatable = false)
  private UUID candidateId;

  @Column(name = "application_id", nullable = false, updatable = false)
  private UUID applicationId;

  @Column(name = "event_status", nullable = false)
  private String eventStatus;

  @Column(name = "recipient_email", nullable = false, updatable = false)
  private String recipientEmail;

  @Column(name = "subject", nullable = false)
  private String subject;

  @Column(name = "message", nullable = false)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_status", nullable = false)
  private DeliveryStatus deliveryStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "error_message")
  private String errorMessage;

  /** Required by JPA. */
  protected Notification() {}

  /** Creates a notification with explicit states — the form the persistence layer reconstitutes. */
  public Notification(
      UUID notificationId,
      UUID candidateId,
      UUID applicationId,
      String eventStatus,
      String recipientEmail,
      String subject,
      String message,
      DeliveryStatus deliveryStatus,
      Instant createdAt,
      Instant sentAt,
      String errorMessage) {
    this.notificationId = notificationId;
    this.candidateId = candidateId;
    this.applicationId = applicationId;
    this.eventStatus = eventStatus;
    this.recipientEmail = recipientEmail;
    this.subject = subject;
    this.message = message;
    this.deliveryStatus = deliveryStatus;
    this.createdAt = createdAt;
    this.sentAt = sentAt;
    this.errorMessage = errorMessage;
  }

  // ===================== Getters =====================

  public UUID getNotificationId() {
    return notificationId;
  }

  public UUID getCandidateId() {
    return candidateId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public String getEventStatus() {
    return eventStatus;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getSubject() {
    return subject;
  }

  public String getMessage() {
    return message;
  }

  public DeliveryStatus getDeliveryStatus() {
    return deliveryStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  // ===================== Setters =====================

  public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
    this.deliveryStatus = deliveryStatus;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
