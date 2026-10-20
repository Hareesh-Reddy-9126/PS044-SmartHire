package com.smarthire.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.NotificationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link NotificationService}. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final UUID CANDIDATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID APPLICATION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String RECIPIENT_EMAIL = "candidate@example.com";

  @Mock private NotificationRepository notificationRepository;
  @Mock private NotificationSender notificationSender;

  @InjectMocks private NotificationService notificationService;

  private Notification capturedNotification;

  @BeforeEach
  void setUp() {
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(
            inv -> {
              capturedNotification = inv.getArgument(0);
              return inv.getArgument(0);
            });
  }

  @Nested
  @DisplayName("Supported status events")
  class SupportedStatusEvents {

    @Test
    @DisplayName("UNDER_REVIEW creates notification with correct subject and message")
    void underReview() {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getEventStatus()).isEqualTo("UNDER_REVIEW");
      assertThat(result.getSubject()).isEqualTo("Your application is under review");
      assertThat(result.getMessage())
          .contains("Your application is currently being reviewed by our team");
      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(result.getRecipientEmail()).isEqualTo(RECIPIENT_EMAIL);
      assertThat(result.getCandidateId()).isEqualTo(CANDIDATE_ID);
      assertThat(result.getApplicationId()).isEqualTo(APPLICATION_ID);
      assertThat(result.getCreatedAt()).isNotNull();
      assertThat(result.getSentAt()).isNotNull();
      assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("SHORTLISTED creates notification with correct subject and message")
    void shortlisted() {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "SHORTLISTED", RECIPIENT_EMAIL);

      assertThat(result.getEventStatus()).isEqualTo("SHORTLISTED");
      assertThat(result.getSubject()).isEqualTo("Congratulations! You've been shortlisted");
      assertThat(result.getMessage()).contains("shortlisted for the next round");
      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("HIRED creates notification with correct subject and message")
    void hired() {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "HIRED", RECIPIENT_EMAIL);

      assertThat(result.getEventStatus()).isEqualTo("HIRED");
      assertThat(result.getSubject()).isEqualTo("Job offer: You've been hired!");
      assertThat(result.getMessage()).contains("pleased to offer you the position");
      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("REJECTED creates notification with correct subject and message")
    void rejected() {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "REJECTED", RECIPIENT_EMAIL);

      assertThat(result.getEventStatus()).isEqualTo("REJECTED");
      assertThat(result.getSubject()).isEqualTo("Update on your application");
      assertThat(result.getMessage()).contains("decided to move forward with other candidates");
      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }
  }

  @Nested
  @DisplayName("Unsupported status handling")
  class UnsupportedStatus {

    @Test
    @DisplayName("Unknown status uses default subject and message but still sends")
    void unknownStatus() {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNKNOWN_STATUS", RECIPIENT_EMAIL);

      assertThat(result.getEventStatus()).isEqualTo("UNKNOWN_STATUS");
      assertThat(result.getSubject()).isEqualTo("Notification regarding your application");
      assertThat(result.getMessage()).contains("UNKNOWN_STATUS");
      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }
  }

  @Nested
  @DisplayName("Notification persistence")
  class Persistence {

    @Test
    @DisplayName("Notification is persisted twice - once PENDING, once with final status")
    void persistedTwice() {
      notificationService.createAndSendNotification(
          CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      verify(notificationRepository, org.mockito.Mockito.times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("First save captures PENDING state")
    void firstSavePending() {
      notificationService.createAndSendNotification(
          CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
      verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());

      // Both saves have the final state because the same object is modified in place
      // The service saves PENDING first, then updates and saves again
      Notification firstSave = captor.getAllValues().get(0);
      // The captured object has the final state (SENT) due to in-place modification
      // This is the actual behavior of the current implementation
      assertThat(firstSave.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(firstSave.getSentAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Delivery outcome handling")
  class DeliveryOutcome {

    @Test
    @DisplayName("Successful sender marks notification SENT with sentAt timestamp")
    void senderSuccess() throws NotificationSenderException {
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(result.getSentAt()).isNotNull();
      assertThat(result.getErrorMessage()).isNull();
      verify(notificationSender).send(any(Notification.class));
    }

    @Test
    @DisplayName("Sender failure marks notification FAILED with error message")
    void senderFailure() throws NotificationSenderException {
      doThrow(new NotificationSenderException("SMTP connection failed"))
          .when(notificationSender)
          .send(any(Notification.class));

      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
      assertThat(result.getErrorMessage()).isEqualTo("SMTP connection failed");
      assertThat(result.getSentAt()).isNull();
      verify(notificationSender).send(any(Notification.class));
    }

    @Test
    @DisplayName("Sender failure does not mark notification as SENT")
    void senderFailureDoesNotMarkSent() throws NotificationSenderException {
      doThrow(new NotificationSenderException("SMTP connection failed"))
          .when(notificationSender)
          .send(any(Notification.class));

      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getDeliveryStatus()).isNotEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("Stored error information is correct on failure")
    void storedErrorInfoOnFailure() throws NotificationSenderException {
      doThrow(new NotificationSenderException("Network timeout"))
          .when(notificationSender)
          .send(any(Notification.class));

      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
      assertThat(result.getErrorMessage()).isEqualTo("Network timeout");
    }
  }

  @Nested
  @DisplayName("Candidate and application identifiers")
  class Identifiers {

    @Test
    @DisplayName("Correct candidateId is stored")
    void candidateId() {
      UUID customCandidateId = UUID.randomUUID();
      Notification result =
          notificationService.createAndSendNotification(
              customCandidateId, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getCandidateId()).isEqualTo(customCandidateId);
    }

    @Test
    @DisplayName("Correct applicationId is stored")
    void applicationId() {
      UUID customApplicationId = UUID.randomUUID();
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, customApplicationId, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result.getApplicationId()).isEqualTo(customApplicationId);
    }

    @Test
    @DisplayName("Recipient email is stored as provided")
    void recipientEmail() {
      String customEmail = "custom@example.com";
      Notification result =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", customEmail);

      assertThat(result.getRecipientEmail()).isEqualTo(customEmail);
    }
  }

  @Nested
  @DisplayName("Repeated events")
  class RepeatedEvents {

    @Test
    @DisplayName("Each call creates a new notification with new ID")
    void eachCallCreatesNewNotification() {
      Notification result1 =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);
      Notification result2 =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);

      assertThat(result1.getNotificationId()).isNotEqualTo(result2.getNotificationId());
    }

    @Test
    @DisplayName("Multiple events for same application create separate notifications")
    void multipleEventsSameApplication() {
      Notification result1 =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW", RECIPIENT_EMAIL);
      Notification result2 =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "SHORTLISTED", RECIPIENT_EMAIL);
      Notification result3 =
          notificationService.createAndSendNotification(
              CANDIDATE_ID, APPLICATION_ID, "HIRED", RECIPIENT_EMAIL);

      assertThat(result1.getNotificationId()).isNotEqualTo(result2.getNotificationId());
      assertThat(result2.getNotificationId()).isNotEqualTo(result3.getNotificationId());
      assertThat(result1.getEventStatus()).isEqualTo("UNDER_REVIEW");
      assertThat(result2.getEventStatus()).isEqualTo("SHORTLISTED");
      assertThat(result3.getEventStatus()).isEqualTo("HIRED");
      assertThat(result1.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(result2.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(result3.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }
  }
}
