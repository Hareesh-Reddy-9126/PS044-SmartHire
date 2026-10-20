package com.smarthire.notification.api;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.smarthire.common.contract.notification.NotificationResponse;
import com.smarthire.common.web.CorrelationId;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for candidate-facing notification endpoints.
 *
 * <p>These endpoints ARE exposed via the API Gateway and allow candidates to view their
 * notifications.
 *
 * <p>Endpoints: GET /api/v1/notifications - lists notifications for the authenticated candidate GET
 * /api/v1/notifications/{id} - gets a specific notification by ID
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(
    name = "Notification Management",
    description = "Endpoints for managing candidate notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

  private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

  private final NotificationRepository notificationRepository;

  public NotificationController(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * Lists notifications for the authenticated candidate.
   *
   * <p>Returns notifications owned by the candidate making the request.
   *
   * @param request the current HTTP request (for correlation ID)
   * @param jwt the authenticated JWT (contains candidate sub claim)
   * @param pageable pagination parameters
   * @return paginated list of notifications for the candidate
   */
  @Operation(
      summary = "List candidate notifications",
      description = "Returns a paginated list of notifications for the authenticated candidate")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Notifications retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PagedModel.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping
  public ResponseEntity<PagedModel<EntityModel<NotificationResponse>>> listNotifications(
      HttpServletRequest request, @AuthenticationPrincipal Jwt jwt, Pageable pageable) {

    UUID candidateId = UUID.fromString(jwt.getSubject());
    log.info(
        "Listing notifications for candidate {} (correlationId: {})",
        candidateId,
        request.getHeader(CorrelationId.HEADER));

    Page<Notification> notificationsPage =
        notificationRepository.findByCandidateId(candidateId, pageable);

    // Convert to HATEOAS models
    Page<EntityModel<NotificationResponse>> notificationsModels =
        notificationsPage.map(
            notification ->
                EntityModel.of(
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
                        notification.getErrorMessage()),
                    linkTo(
                            methodOn(NotificationController.class)
                                .getNotification(request, jwt, notification.getNotificationId()))
                        .withSelfRel()));

    PagedModel<EntityModel<NotificationResponse>> pagedModel =
        PagedModel.of(
            notificationsModels.getContent(),
            new PagedModel.PageMetadata(
                notificationsModels.getNumber(),
                notificationsModels.getSize(),
                notificationsModels.getTotalElements()));

    return ResponseEntity.ok(pagedModel);
  }

  /**
   * Gets a specific notification by ID for the authenticated candidate.
   *
   * <p>Ensures the candidate owns the notification before returning it.
   *
   * @param request the current HTTP request (for correlation ID)
   * @param jwt the authenticated JWT (contains candidate sub claim)
   * @param notificationId the notification ID to retrieve
   * @return the notification if owned by the candidate
   */
  @Operation(
      summary = "Get notification by ID",
      description = "Returns a specific notification if it belongs to the authenticated candidate")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Notification retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = NotificationResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - notification not owned by candidate",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Notification not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/{notificationId}")
  public ResponseEntity<EntityModel<NotificationResponse>> getNotification(
      HttpServletRequest request,
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID notificationId) {

    UUID candidateId = UUID.fromString(jwt.getSubject());
    log.info(
        "Getting notification {} for candidate {} (correlationId: {})",
        notificationId,
        candidateId,
        request.getHeader(CorrelationId.HEADER));

    Notification notification =
        notificationRepository
            .findByNotificationId(notificationId)
            .orElseThrow(NotificationNotFoundException::new);

    // Check ownership - candidate can only see their own notifications
    if (!notification.getCandidateId().equals(candidateId)) {
      throw new NotificationForbiddenException();
    }

    EntityModel<NotificationResponse> response =
        EntityModel.of(
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
                notification.getErrorMessage()),
            linkTo(
                    methodOn(NotificationController.class)
                        .getNotification(request, jwt, notificationId))
                .withSelfRel(),
            linkTo(methodOn(NotificationController.class).listNotifications(request, jwt, null))
                .withRel("notifications"));

    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(NotificationNotFoundException.class)
  public ProblemDetail handleNotificationNotFound(
      NotificationNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found");
    problem.setTitle("Notification Not Found");
    problem.setInstance(URI.create(request.getRequestURI()));
    String correlationId = org.slf4j.MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }

  @ExceptionHandler(NotificationForbiddenException.class)
  public ProblemDetail handleNotificationForbidden(
      NotificationForbiddenException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "You do not have permission to access this notification");
    problem.setTitle("Access Denied");
    problem.setInstance(URI.create(request.getRequestURI()));
    String correlationId = org.slf4j.MDC.get(CorrelationId.MDC_KEY);
    if (correlationId != null) {
      problem.setProperty("correlationId", correlationId);
    }
    return problem;
  }
}

/** Exception thrown when a notification is not found. */
class NotificationNotFoundException extends RuntimeException {}

/** Exception thrown when a candidate tries to access a notification they don't own. */
class NotificationForbiddenException extends RuntimeException {}
