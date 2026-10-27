package com.smarthire.notification.service;

import com.smarthire.notification.config.NotificationSenderProperties;
import com.smarthire.notification.domain.Notification;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Email delivery through the Resend HTTP API ({@code POST /emails}).
 *
 * <p>Resend is used instead of SMTP when the deployment has an API key but no mailbox: the free
 * tier covers a demo deployment, and delivery happens over HTTPS so no outbound SMTP port is
 * needed.
 *
 * <p>The sender is deliberately quiet: it logs the notification id and failure class only, never
 * recipient addresses, message bodies or the API key. Rate limits (429) and server errors (5xx) are
 * retried with backoff; other 4xx responses (bad key, unverified sender domain) fail immediately
 * because retrying cannot succeed.
 *
 * <p>Each attempt carries an {@code Idempotency-Key} derived from the notification id so a retry
 * cannot deliver the same message twice.
 */
public class ResendNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(ResendNotificationSender.class);

  private final RestClient client;
  private final NotificationSenderProperties properties;

  public ResendNotificationSender(RestClient client, NotificationSenderProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public void send(Notification notification) throws NotificationSenderException {
    int maxAttempts = Math.max(1, properties.getMaxAttempts());
    long backoffMs = Math.max(0, properties.getRetryBackoffMs());
    Exception lastFailure = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        client
            .post()
            .uri("/emails")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", notification.getNotificationId().toString())
            .body(payload(notification))
            .retrieve()
            .toBodilessEntity();
        if (attempt > 1) {
          log.info(
              "Notification {} delivered on attempt {}", notification.getNotificationId(), attempt);
        }
        return;
      } catch (RestClientResponseException failure) {
        lastFailure = failure;
        boolean retryable =
            failure.getStatusCode().value() == 429 || failure.getStatusCode().is5xxServerError();
        log.warn(
            "Notification {} Resend attempt {}/{} failed with HTTP {} ({})",
            notification.getNotificationId(),
            attempt,
            maxAttempts,
            failure.getStatusCode().value(),
            retryable ? "retryable" : "not retryable");
        if (!retryable || attempt == maxAttempts) {
          break;
        }
      } catch (RestClientException failure) {
        // Connection reset, timeout, DNS failure: always worth another attempt.
        lastFailure = failure;
        log.warn(
            "Notification {} Resend attempt {}/{} failed ({})",
            notification.getNotificationId(),
            attempt,
            maxAttempts,
            failure.getClass().getSimpleName());
        if (attempt == maxAttempts) {
          break;
        }
      }

      if (backoffMs > 0) {
        try {
          Thread.sleep(backoffMs * attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new NotificationSenderException("Resend delivery interrupted", interrupted);
        }
      }
    }

    throw new NotificationSenderException(
        "Resend delivery failed after " + maxAttempts + " attempt(s)", lastFailure);
  }

  private Map<String, Object> payload(Notification notification) {
    return Map.of(
        "from", properties.getResend().getFrom(),
        "to", List.of(notification.getRecipientEmail()),
        "subject", notification.getSubject() == null ? "" : notification.getSubject(),
        "text", notification.getMessage() == null ? "" : notification.getMessage());
  }
}
