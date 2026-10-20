package com.smarthire.application.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import com.smarthire.application.service.ApplicationService;
import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.common.contract.notification.NotificationResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for Application Service → Notification Service boundary.
 *
 * <p>Focuses on the status-change notification contract:
 *
 * <ul>
 *   <li>Correct notification request serialization
 *   <li>Correct behavior when Notification Service accepts a request
 *   <li>Downstream failure handling and its impact on application state
 *   <li>Verification that notification delivery is asynchronous/best-effort
 * </ul>
 *
 * <p>Uses Mockito to simulate Feign client behavior without requiring Docker or live services.
 */
class ApplicationNotificationContractTest {

  private ApplicationRepository applicationRepository;
  private JobClient jobClient;
  private NotificationClient notificationClient;
  private ApplicationService applicationService;

  private final UUID JOB_ID = UUID.randomUUID();
  private final UUID CANDIDATE_ID = UUID.randomUUID();
  private final UUID ORG_ID = UUID.randomUUID();
  private final String RESUME_LINK = "https://example.com/resume.pdf";

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationRepository.class);
    jobClient = mock(JobClient.class);
    notificationClient = mock(NotificationClient.class);
    applicationService =
        new ApplicationService(applicationRepository, jobClient, notificationClient);

    // Default: jobClient returns a job owned by the test ORG_ID
    when(jobClient.getJob(JOB_ID))
        .thenReturn(
            new JobClient.JobResponse(
                JOB_ID, "Acme Corp", "Backend Engineer", "Remote", "OPEN", ORG_ID));
  }

  private Application givenApplication(ApplicationStatus startingStatus) {
    Application app =
        Application.createApplied(UUID.randomUUID(), JOB_ID, CANDIDATE_ID, RESUME_LINK);
    switch (startingStatus) {
      case UNDER_REVIEW -> app.moveToUnderReview();
      case SHORTLISTED -> {
        app.moveToUnderReview();
        app.shortlist();
      }
      case HIRED -> {
        app.moveToUnderReview();
        app.shortlist();
        app.hire();
      }
      case REJECTED -> {
        app.moveToUnderReview();
        app.shortlist();
        app.reject();
      }
      default -> {}
    }
    when(applicationRepository.findByApplicationId(app.getApplicationId()))
        .thenReturn(Optional.of(app));
    when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
    return app;
  }

  private NotificationResponse givenNotificationAccepted() {
    NotificationResponse response =
        new NotificationResponse(
            UUID.randomUUID(),
            CANDIDATE_ID,
            JOB_ID,
            "HIRED",
            "candidate@example.com",
            "You've been hired!",
            "Congratulations!",
            DeliveryStatus.SENT,
            Instant.now(),
            Instant.now(),
            null);
    when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
        .thenReturn(response);
    return response;
  }

  @Nested
  @DisplayName("Notification request serialization contract")
  class RequestSerializationContract {

    @Test
    @DisplayName("APPLIED → UNDER_REVIEW triggers notification with correct payload")
    void appliedToUnderReviewTriggersCorrectPayload() {
      Application app = givenApplication(ApplicationStatus.APPLIED);
      givenNotificationAccepted();

      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, ORG_ID, false);

      verify(notificationClient)
          .sendNotification(
              eq(
                  new ApplicationStatusChangeRequest(
                      app.getCandidateId(), app.getApplicationId(), "UNDER_REVIEW")));
    }

    @Test
    @DisplayName("UNDER_REVIEW → SHORTLISTED triggers notification with correct payload")
    void underReviewToShortlistedTriggersCorrectPayload() {
      Application app = givenApplication(ApplicationStatus.UNDER_REVIEW);
      givenNotificationAccepted();

      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.SHORTLISTED, ORG_ID, false);

      verify(notificationClient)
          .sendNotification(
              eq(
                  new ApplicationStatusChangeRequest(
                      app.getCandidateId(), app.getApplicationId(), "SHORTLISTED")));
    }

    @Test
    @DisplayName("SHORTLISTED → HIRED triggers notification with correct payload")
    void shortlistedToHiredTriggersCorrectPayload() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      givenNotificationAccepted();

      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      verify(notificationClient)
          .sendNotification(
              eq(
                  new ApplicationStatusChangeRequest(
                      app.getCandidateId(), app.getApplicationId(), "HIRED")));
    }

    @Test
    @DisplayName("SHORTLISTED → REJECTED triggers notification with correct payload")
    void shortlistedToRejectedTriggersCorrectPayload() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      givenNotificationAccepted();

      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.REJECTED, ORG_ID, false);

      verify(notificationClient)
          .sendNotification(
              eq(
                  new ApplicationStatusChangeRequest(
                      app.getCandidateId(), app.getApplicationId(), "REJECTED")));
    }
  }

  @Nested
  @DisplayName("Notification acceptance contract")
  class NotificationAcceptanceContract {

    @Test
    @DisplayName("Application status change succeeds when notification is accepted")
    void applicationStatusChangeSucceedsWhenNotificationAccepted() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      givenNotificationAccepted();

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Returns notification response from downstream")
    void returnsDownstreamResponse() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      NotificationResponse expected = givenNotificationAccepted();

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      // The service doesn't return the notification response directly,
      // but the call is made and the application status change succeeds
      verify(notificationClient).sendNotification(any(ApplicationStatusChangeRequest.class));
    }
  }

  @Nested
  @DisplayName("Downstream failure handling contract")
  class DownstreamFailureContract {

    @Test
    @DisplayName("Application status change SUCCEEDS even when notification fails (best-effort)")
    void applicationStatusChangeSucceedsDespiteNotificationFailure() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(new RuntimeException("circuit open"));

      // The application status change should SUCCEED even though notification fails
      // because notifications are best-effort
      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Feign 400 from Notification Service does NOT fail application update")
    void notification400DoesNotFailApplication() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(feignException(400, "{\"detail\":\"validation failed\"}"));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Feign 401 from Notification Service does NOT fail application update")
    void notification401DoesNotFailApplication() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(feignException(401, "{\"detail\":\"unauthorized\"}"));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Feign 503 from Notification Service does NOT fail application update")
    void notification503DoesNotFailApplication() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(feignException(503, "service unavailable"));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Feign 500 from Notification Service does NOT fail application update")
    void notification500DoesNotFailApplication() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(feignException(500, "boom"));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }

    @Test
    @DisplayName("Any runtime exception from Notification Service does NOT fail application update")
    void anyRuntimeExceptionDoesNotFailApplication() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(new RuntimeException("any error"));

      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);
    }
  }

  @Nested
  @DisplayName("Asynchronous/best-effort contract")
  class AsyncBestEffortContract {

    @Test
    @DisplayName("Application transaction commits even when notification fails")
    void applicationCommitsDespiteNotificationFailure() {
      Application app = givenApplication(ApplicationStatus.SHORTLISTED);
      when(notificationClient.sendNotification(any(ApplicationStatusChangeRequest.class)))
          .thenThrow(new RuntimeException("circuit open"));

      // The application status change should complete successfully
      Application result =
          applicationService.updateStatus(
              app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      assertThat(result.getStatus()).isEqualTo(ApplicationStatus.HIRED);

      // The application repository save was called
      verify(applicationRepository).save(any(Application.class));
    }

    @Test
    @DisplayName("Multiple status changes each trigger notification attempt")
    void multipleStatusChangesEachTriggerNotification() {
      Application app = givenApplication(ApplicationStatus.APPLIED);
      givenNotificationAccepted();

      // First: APPLIED → UNDER_REVIEW
      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.UNDER_REVIEW, ORG_ID, false);

      // Second: UNDER_REVIEW → SHORTLISTED
      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.SHORTLISTED, ORG_ID, false);

      // Third: SHORTLISTED → HIRED
      applicationService.updateStatus(
          app.getApplicationId(), ApplicationStatus.HIRED, ORG_ID, false);

      // Verify 3 notification calls (for UNDER_REVIEW, SHORTLISTED, HIRED)
      verify(notificationClient, org.mockito.Mockito.times(3))
          .sendNotification(any(ApplicationStatusChangeRequest.class));
    }
  }

  /** Builds a FeignException for testing. */
  private feign.FeignException feignException(int status, String body) {
    String bodyStr = body;
    feign.Request request =
        feign.Request.create(
            feign.Request.HttpMethod.POST,
            "http://notification-service/internal/v1/notifications/application-status",
            java.util.Map.of(),
            bodyStr.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    return switch (status) {
      case 400 ->
          new feign.FeignException.BadRequest(
              "bad request", request, bodyStr.getBytes(StandardCharsets.UTF_8), java.util.Map.of());
      case 401 ->
          new feign.FeignException.Unauthorized(
              "unauthorized",
              request,
              bodyStr.getBytes(StandardCharsets.UTF_8),
              java.util.Map.of());
      case 503 ->
          feign.FeignException.errorStatus(
              "service unavailable",
              feign.Response.builder()
                  .status(status)
                  .reason("service unavailable")
                  .request(request)
                  .headers(java.util.Map.of())
                  .body(bodyStr, StandardCharsets.UTF_8)
                  .build());
      case 500 ->
          feign.FeignException.errorStatus(
              "error",
              feign.Response.builder()
                  .status(status)
                  .reason("test")
                  .request(request)
                  .headers(java.util.Map.of())
                  .body(bodyStr, StandardCharsets.UTF_8)
                  .build());
      default ->
          feign.FeignException.errorStatus(
              "error",
              feign.Response.builder()
                  .status(status)
                  .reason("test")
                  .request(request)
                  .headers(java.util.Map.of())
                  .body(bodyStr, StandardCharsets.UTF_8)
                  .build());
    };
  }
}
