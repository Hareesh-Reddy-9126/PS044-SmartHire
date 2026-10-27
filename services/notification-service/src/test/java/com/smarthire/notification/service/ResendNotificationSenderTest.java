package com.smarthire.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.notification.config.NotificationSenderProperties;
import com.smarthire.notification.domain.Notification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Unit tests for {@link ResendNotificationSender}. */
class ResendNotificationSenderTest {

  private static final String BASE_URL = "https://api.resend.test";
  private static final UUID NOTIFICATION_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String RECIPIENT = "candidate@example.com";

  private MockRestServiceServer server;
  private NotificationSenderProperties properties;
  private ResendNotificationSender sender;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Authorization", "Bearer re_test_key");
    server = MockRestServiceServer.bindTo(builder).build();

    properties = new NotificationSenderProperties();
    properties.setType("resend");
    properties.setMaxAttempts(3);
    properties.setRetryBackoffMs(0);
    properties.getResend().setApiKey("re_test_key");
    properties.getResend().setFrom("SmartHire <onboarding@resend.dev>");

    sender = new ResendNotificationSender(builder.build(), properties);
  }

  @Test
  @DisplayName("posts the message to /emails with bearer auth and an idempotency key")
  void sendsExpectedRequest() throws NotificationSenderException {
    server
        .expect(ExpectedCount.once(), requestTo(BASE_URL + "/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer re_test_key"))
        .andExpect(header("Idempotency-Key", NOTIFICATION_ID.toString()))
        .andExpect(jsonPath("$.from").value("SmartHire <onboarding@resend.dev>"))
        .andExpect(jsonPath("$.to[0]").value(RECIPIENT))
        .andExpect(jsonPath("$.subject").value("Application update"))
        .andExpect(jsonPath("$.text").value("Your application moved to INTERVIEW."))
        .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

    sender.send(notification());

    server.verify();
  }

  @Test
  @DisplayName("retries a rate-limited request and succeeds on the next attempt")
  void retriesRateLimit() throws NotificationSenderException {
    server
        .expect(ExpectedCount.once(), requestTo(BASE_URL + "/emails"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(ExpectedCount.once(), requestTo(BASE_URL + "/emails"))
        .andRespond(withSuccess("{\"id\":\"email_124\"}", MediaType.APPLICATION_JSON));

    sender.send(notification());

    server.verify();
  }

  @Test
  @DisplayName("does not retry a rejected request such as an unverified sender")
  void doesNotRetryClientError() {
    server
        .expect(ExpectedCount.once(), requestTo(BASE_URL + "/emails"))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Domain not verified\"}"));

    assertThatThrownBy(() -> sender.send(notification()))
        .isInstanceOf(NotificationSenderException.class)
        .hasMessageContaining("3 attempt");

    server.verify();
  }

  @Test
  @DisplayName("gives up after the configured attempts and reports the failure")
  void failsAfterMaxAttempts() {
    server
        .expect(ExpectedCount.times(3), requestTo(BASE_URL + "/emails"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> sender.send(notification()))
        .isInstanceOf(NotificationSenderException.class)
        .hasMessageContaining("3 attempt");

    server.verify();
  }

  @Test
  @DisplayName("exposes the resend channel through the sender factory")
  void factoryBuildsResendSender() {
    NotificationSender built =
        new com.smarthire.notification.config.NotificationSenderConfig()
            .notificationSender(properties);

    assertThat(built).isInstanceOf(ResendNotificationSender.class);
  }

  private Notification notification() {
    return new Notification(
        NOTIFICATION_ID,
        UUID.fromString("44444444-4444-4444-4444-444444444444"),
        UUID.fromString("55555555-5555-5555-5555-555555555555"),
        "INTERVIEW",
        RECIPIENT,
        "Application update",
        "Your application moved to INTERVIEW.",
        DeliveryStatus.PENDING,
        Instant.parse("2026-01-01T00:00:00Z"),
        null,
        null);
  }
}
