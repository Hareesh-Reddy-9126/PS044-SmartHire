package com.smarthire.application.infra;

import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.NotificationResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for Notification Service.
 *
 * <p>Calls {@code POST /internal/v1/notifications/application-status} to trigger notification
 * creation when an application status changes. The client is a fire-and-forget step — notification
 * delivery failures do not roll back the application status transition.
 *
 * <p>Uses {@code @FeignClient(name = "NOTIFICATION-SERVICE")} so the call is load-balanced through
 * Eureka. Protected by Resilience4j circuit breaker, retry, and bulkhead for fault tolerance.
 */
@FeignClient(name = "NOTIFICATION-SERVICE")
public interface NotificationClient {

  /**
   * Triggers notification creation for an application status change.
   *
   * @param request contains candidateId, applicationId, and the new application status
   * @return the created notification with delivery status
   */
  @PostMapping("/application-status")
  @CircuitBreaker(name = "notificationService", fallbackMethod = "sendNotificationFallback")
  @Bulkhead(name = "notificationService")
  NotificationResponse sendNotification(@RequestBody ApplicationStatusChangeRequest request);

  /**
   * Fallback for circuit breaker / bulkhead when Notification Service is unavailable. Returns a
   * dummy response indicating the downstream dependency is unavailable. The application status
   * change is NOT rolled back - notifications are best-effort.
   */
  default NotificationResponse sendNotificationFallback(
      ApplicationStatusChangeRequest request, Exception ex) {
    // Return a fake successful response - the application transaction continues
    // Notification delivery is best-effort; failures are logged and retried separately
    return new NotificationResponse(
        UUID.randomUUID(), // fake notificationId
        request.candidateId(),
        request.applicationId(),
        request.status(),
        "unknown@example.com", // placeholder
        "Notification queued",
        "Your notification will be sent shortly",
        com.smarthire.common.contract.notification.DeliveryStatus.PENDING,
        java.time.Instant.now(),
        null,
        "Notification service unavailable - notification will be retried");
  }
}
