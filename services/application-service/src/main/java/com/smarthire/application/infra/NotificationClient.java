package com.smarthire.application.infra;

import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.NotificationResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
 * Eureka. Protected by Resilience4j circuit breaker, retry, and bulkhead for fault tolerance. The
 * aspect order configured in {@code application.yml} makes the circuit breaker wrap the retry, so
 * transient failures are retried before the fallback is considered.
 */
@FeignClient(name = "NOTIFICATION-SERVICE", path = "/internal/v1/notifications")
public interface NotificationClient {

  /**
   * Triggers notification creation for an application status change.
   *
   * @param request contains candidateId, applicationId, and the new application status
   * @return the created notification with delivery status
   */
  @PostMapping("/application-status")
  @CircuitBreaker(name = "notificationService", fallbackMethod = "sendNotificationFallback")
  @Retry(name = "notificationService")
  @Bulkhead(name = "notificationService")
  NotificationResponse sendNotification(@RequestBody ApplicationStatusChangeRequest request);

  /**
   * Fallback when Notification Service is unavailable (retries exhausted, circuit open, or bulkhead
   * full). Returns a placeholder response with {@code PENDING} status. The application status
   * change is NOT rolled back - notifications are best-effort.
   *
   * <p>A 4xx means the request itself was rejected (contract, authentication or routing problem),
   * not that the service is unavailable, so it is rethrown rather than disguised as a pending
   * delivery; the caller logs it.
   */
  default NotificationResponse sendNotificationFallback(
      ApplicationStatusChangeRequest request, Exception ex) {
    if (ex instanceof FeignException.FeignClientException clientError) {
      throw clientError;
    }
    // The application transaction continues; the notification is not persisted downstream.
    return new NotificationResponse(
        UUID.randomUUID(), // placeholder notificationId
        request.candidateId(),
        request.applicationId(),
        request.status(),
        "unknown@example.com", // placeholder
        "Notification queued",
        "Your notification will be sent shortly",
        com.smarthire.common.contract.notification.DeliveryStatus.PENDING,
        java.time.Instant.now(),
        null,
        "Notification service unavailable - notification not delivered");
  }
}
