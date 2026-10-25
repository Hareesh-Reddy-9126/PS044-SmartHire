package com.smarthire.notification.service;

import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification service that creates notifications from application status changes and attempts
 * delivery via a {@link NotificationSender}.
 *
 * <p>The service is responsible for:
 *
 * <ul>
 *   <li>Mapping application statuses to notification templates (UNDER_REVIEW, SHORTLISTED, HIRED,
 *       REJECTED)
 *   <li>Creating Notification entities in PENDING state
 *   <li>Attempting delivery via the configured NotificationSender
 *   <li>Updating the notification to SENT or FAILED based on delivery outcome
 * </ul>
 *
 * <p>Delivery failures do NOT roll back the application status transition - they are processed
 * asynchronously and the notification is marked FAILED for retry/manual handling.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepository;
  private final NotificationSender notificationSender;

  public NotificationService(
      NotificationRepository notificationRepository, NotificationSender notificationSender) {
    this.notificationRepository = notificationRepository;
    this.notificationSender = notificationSender;
  }

  /**
   * Creates and sends a notification for the given application status change.
   *
   * <p>This method is called by Application Service when an application transitions to a notifiable
   * status (UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED).
   *
   * @param candidateId the candidate UUID (from JWT sub)
   * @param applicationId the application UUID that changed status
   * @param eventStatus the new application status that triggered the notification
   * @param recipientEmail the email address to send the notification to
   * @return the created notification (with delivery status updated)
   */
  @Transactional
  public Notification createAndSendNotification(
      UUID candidateId, UUID applicationId, String eventStatus, String recipientEmail) {
    // Same application/status transition must not create a second notification when the caller
    // retries or when the same transition is replayed.
    var existing =
        notificationRepository.findFirstByApplicationIdAndEventStatusOrderByCreatedAtDesc(
            applicationId, eventStatus);

    if (existing.isPresent()) {
      Notification prior = existing.get();
      if (prior.getDeliveryStatus() == DeliveryStatus.SENT) {
        log.debug(
            "Duplicate notification request suppressed for application {} status {}",
            applicationId,
            eventStatus);
        return prior;
      }
      if (prior.getDeliveryStatus() == DeliveryStatus.PENDING) {
        return prior;
      }
      // FAILED notifications may be retried by the same request; clear the previous error first.
      prior.setErrorMessage(null);
      prior.setSentAt(null);
      prior.setDeliveryStatus(DeliveryStatus.PENDING);
      prior = notificationRepository.save(prior);
      return attemptDelivery(prior);
    }

    // Create the notification in PENDING state
    Notification notification =
        createNotification(candidateId, applicationId, eventStatus, recipientEmail);

    // Save to get the ID
    notification = notificationRepository.save(notification);
    return attemptDelivery(notification);
  }

  private Notification attemptDelivery(Notification notification) {
    // Attempt delivery
    try {
      notificationSender.send(notification);
      // Mark as sent on success
      notification.setDeliveryStatus(DeliveryStatus.SENT);
      notification.setSentAt(Instant.now());
      log.info("Notification {} sent successfully", notification.getNotificationId());
    } catch (NotificationSenderException e) {
      // Mark as failed on error - delivery failure does not roll back application status
      notification.setDeliveryStatus(DeliveryStatus.FAILED);
      notification.setErrorMessage(e.getMessage());
      log.warn(
          "Notification {} failed to send: {}", notification.getNotificationId(), e.getMessage());
    }

    // Save the final state (SENT or FAILED)
    return notificationRepository.save(notification);
  }

  /**
   * Creates a notification domain object from the given parameters.
   *
   * <p>This method does not persist the notification - it only creates the domain object.
   *
   * @param candidateId the candidate UUID
   * @param applicationId the application UUID
   * @param eventStatus the application status that triggered the notification
   * @param recipientEmail the recipient email address
   * @return a new Notification in PENDING state
   */
  private Notification createNotification(
      UUID candidateId, UUID applicationId, String eventStatus, String recipientEmail) {
    String subject = generateSubject(eventStatus);
    String message = generateMessage(eventStatus);

    return new Notification(
        UUID.randomUUID(), // notificationId
        candidateId,
        applicationId,
        eventStatus,
        recipientEmail,
        subject,
        message,
        DeliveryStatus.PENDING, // deliveryStatus starts as PENDING
        Instant.now(), // createdAt
        null, // sentAt (will be set when sent)
        null // errorMessage (null unless failed)
        );
  }

  /**
   * Generates an email subject based on the application event status.
   *
   * @param eventStatus the application status (UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED)
   * @return the email subject line
   */
  private String generateSubject(String eventStatus) {
    return switch (eventStatus) {
      case "UNDER_REVIEW" -> "Your application is under review";
      case "SHORTLISTED" -> "Congratulations! You've been shortlisted";
      case "HIRED" -> "Job offer: You've been hired!";
      case "REJECTED" -> "Update on your application";
      default -> "Notification regarding your application";
    };
  }

  /**
   * Generates an email message body based on the application event status.
   *
   * @param eventStatus the application status (UNDER_REVIEW, SHORTLISTED, HIRED, REJECTED)
   * @return the email message body
   */
  private String generateMessage(String eventStatus) {
    return switch (eventStatus) {
      case "UNDER_REVIEW" ->
          "Thank you for your application! Your application is currently being reviewed by our team. "
              + "We'll contact you if we need any additional information.";
      case "SHORTLISTED" ->
          "Great news! Your application has been shortlisted for the next round. "
              + "Our team will be in touch soon to schedule an interview.";
      case "HIRED" ->
          "Congratulations! We're pleased to offer you the position. "
              + "Please review the attached offer details and let us know if you have any questions.";
      case "REJECTED" ->
          "Thank you for your interest in our company. After careful consideration, "
              + "we've decided to move forward with other candidates for this position. "
              + "We encourage you to apply for future openings that match your skills.";
      default ->
          "This is a notification regarding your application status change to: " + eventStatus;
    };
  }
}
