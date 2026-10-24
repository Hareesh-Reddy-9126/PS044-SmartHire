package com.smarthire.notification.api;

import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.NotificationResponse;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.UserDirectoryClient;
import com.smarthire.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST controller for triggering notifications from other services.
 *
 * <p>This endpoint is NOT exposed via the API Gateway - it is for service-to-service communication
 * only (e.g., Application Service → Notification Service).
 *
 * <p>Accepts application status change events and creates/sends notifications.
 */
@RestController
@RequestMapping("/internal/v1/notifications")
@Validated
@Tag(
    name = "Internal Notification API",
    description =
        "Internal endpoints for service-to-service notification triggering (NOT exposed via API Gateway)")
public class NotificationInternalController {

  private final NotificationService notificationService;
  private final UserDirectoryClient userDirectoryClient;

  public NotificationInternalController(
      NotificationService notificationService, UserDirectoryClient userDirectoryClient) {
    this.notificationService = notificationService;
    this.userDirectoryClient = userDirectoryClient;
  }

  /**
   * Processes an application status change event from Application Service.
   *
   * <p>Creates and sends a notification for the specified candidate/application/status change.
   *
   * @param request contains candidateId, applicationId, and the new application status
   * @return the created notification with delivery status
   */
  @Operation(
      summary = "Process application status change (internal)",
      description =
          "Internal endpoint for Application Service to trigger notification creation when an application status changes. Creates and sends a notification email to the candidate.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "202",
            description = "Notification creation accepted",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = NotificationResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "")
  @PostMapping("/application-status")
  public ResponseEntity<NotificationResponse> processApplicationStatus(
      @RequestBody @Valid ApplicationStatusChangeRequest request) {
    // A notification must be addressed to the candidate's real mailbox. The candidate id comes
    // from application-service; auth-service is the only owner of the email address, so resolve
    // it here instead of fabricating one from the id. A failed lookup fails the request: sending
    // to a made-up address would be worse than not sending at all.
    String recipientEmail = userDirectoryClient.getEmail(request.candidateId()).email();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      throw new IllegalStateException(
          "auth-service returned no email for candidate " + request.candidateId());
    }

    Notification notification =
        notificationService.createAndSendNotification(
            request.candidateId(), request.applicationId(), request.status(), recipientEmail);

    NotificationResponse response =
        new NotificationResponse(
            notification.getNotificationId(),
            notification.getCandidateId(),
            notification.getApplicationId(),
            notification.getEventStatus(),
            notification.getRecipientEmail(),
            notification.getSubject(),
            notification.getMessage(),
            notification.getDeliveryStatus(),
            notification.getCreatedAt(),
            notification.getSentAt(),
            notification.getErrorMessage());

    return ResponseEntity.accepted().body(response);
  }
}
