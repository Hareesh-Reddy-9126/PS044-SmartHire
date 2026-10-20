package com.smarthire.notification.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.common.web.GlobalExceptionHandler;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.NotificationRepository;
import com.smarthire.notification.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** WebMvc tests for {@link NotificationController} (candidate-facing endpoints). */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.notification.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.notification.jwt.issuer=https://smarthire.local/auth",
      "smarthire.notification.jwt.audience=smarthire"
    })
class NotificationControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private NotificationRepository notificationRepository;

  private static final UUID CANDIDATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID NOTIFICATION_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID OTHER_CANDIDATE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String TOKEN = "test-token";

  private Jwt candidateJwt;
  private Jwt otherCandidateJwt;

  @BeforeEach
  void setUp() {
    candidateJwt =
        Jwt.withTokenValue(TOKEN)
            .header("alg", "RS256")
            .subject(CANDIDATE_ID.toString())
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .claim("roles", List.of("CANDIDATE"))
            .build();

    otherCandidateJwt =
        Jwt.withTokenValue("other-token")
            .header("alg", "RS256")
            .subject(OTHER_CANDIDATE_ID.toString())
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .claim("roles", List.of("CANDIDATE"))
            .build();

    when(jwtDecoder.decode(TOKEN)).thenReturn(candidateJwt);
    when(jwtDecoder.decode("other-token")).thenReturn(otherCandidateJwt);
  }

  private Notification createNotification(
      UUID id,
      UUID candidateId,
      UUID applicationId,
      String eventStatus,
      DeliveryStatus deliveryStatus) {
    return new Notification(
        id,
        candidateId,
        applicationId,
        eventStatus,
        "candidate@example.com",
        "Test Subject",
        "Test Message",
        deliveryStatus,
        Instant.now(),
        deliveryStatus == DeliveryStatus.SENT ? Instant.now() : null,
        null);
  }

  @Nested
  @DisplayName("List notifications")
  class ListNotifications {

    @Test
    @DisplayName("Returns paginated notifications for authenticated candidate")
    void listOwnNotifications() throws Exception {
      Pageable pageable = PageRequest.of(0, 20);
      Notification n1 =
          createNotification(
              NOTIFICATION_ID,
              CANDIDATE_ID,
              UUID.randomUUID(),
              "UNDER_REVIEW",
              DeliveryStatus.SENT);
      Notification n2 =
          createNotification(
              UUID.randomUUID(), CANDIDATE_ID, UUID.randomUUID(), "HIRED", DeliveryStatus.SENT);
      Page<Notification> page = new PageImpl<>(List.of(n1, n2), pageable, 2);

      when(notificationRepository.findByCandidateId(CANDIDATE_ID, pageable)).thenReturn(page);

      mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$._embedded.notificationResponseList").isArray())
          .andExpect(jsonPath("$._embedded.notificationResponseList.length()").value(2))
          .andExpect(
              jsonPath("$._embedded.notificationResponseList[0].candidateId")
                  .value(CANDIDATE_ID.toString()))
          .andExpect(
              jsonPath("$._embedded.notificationResponseList[0].eventStatus").value("UNDER_REVIEW"))
          .andExpect(jsonPath("$._embedded.notificationResponseList[1].eventStatus").value("HIRED"))
          .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("Returns empty page when candidate has no notifications")
    void emptyList() throws Exception {
      Pageable pageable = PageRequest.of(0, 20);
      Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);

      when(notificationRepository.findByCandidateId(CANDIDATE_ID, pageable)).thenReturn(page);

      mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("Candidate only sees their own notifications")
    void ownershipFilter() throws Exception {
      Pageable pageable = PageRequest.of(0, 20);
      Notification n1 =
          createNotification(
              NOTIFICATION_ID,
              CANDIDATE_ID,
              UUID.randomUUID(),
              "UNDER_REVIEW",
              DeliveryStatus.SENT);
      Page<Notification> page = new PageImpl<>(List.of(n1), pageable, 1);

      when(notificationRepository.findByCandidateId(CANDIDATE_ID, pageable)).thenReturn(page);

      mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$._embedded.notificationResponseList[0].candidateId")
                  .value(CANDIDATE_ID.toString()));
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticated() throws Exception {
      mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid token returns 401")
    void invalidToken() throws Exception {
      when(jwtDecoder.decode("invalid-token"))
          .thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("Invalid token"));

      mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("Get notification by ID")
  class GetNotification {

    @Test
    @DisplayName("Returns notification when candidate owns it")
    void getOwnNotification() throws Exception {
      Notification notification =
          createNotification(
              NOTIFICATION_ID,
              CANDIDATE_ID,
              UUID.randomUUID(),
              "UNDER_REVIEW",
              DeliveryStatus.SENT);

      when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
          .thenReturn(Optional.of(notification));

      mvc.perform(
              get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                  .header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.notificationId").value(NOTIFICATION_ID.toString()))
          .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()))
          .andExpect(jsonPath("$.eventStatus").value("UNDER_REVIEW"))
          .andExpect(jsonPath("$.deliveryStatus").value("SENT"));
    }

    @Test
    @DisplayName("Returns 404 when notification does not exist")
    void notFound() throws Exception {
      when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
          .thenReturn(Optional.empty());

      mvc.perform(
              get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                  .header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.title").value("Notification Not Found"))
          .andExpect(jsonPath("$.detail").value("Notification not found"));
    }

    @Test
    @DisplayName("Returns 403 when candidate tries to access another's notification")
    void forbidden() throws Exception {
      Notification notification =
          createNotification(
              NOTIFICATION_ID,
              OTHER_CANDIDATE_ID,
              UUID.randomUUID(),
              "UNDER_REVIEW",
              DeliveryStatus.SENT);

      when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
          .thenReturn(Optional.of(notification));

      mvc.perform(
              get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                  .header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.title").value("Access Denied"))
          .andExpect(
              jsonPath("$.detail").value("You do not have permission to access this notification"));
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticated() throws Exception {
      mvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("RFC 9457 error format")
  class Rfc9457Errors {

    @Test
    @DisplayName("404 error has correct structure")
    void notFoundStructure() throws Exception {
      when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
          .thenReturn(Optional.empty());

      mvc.perform(
              get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                  .header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Notification Not Found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Notification not found"))
          .andExpect(jsonPath("$.instance").exists());
    }

    @Test
    @DisplayName("403 error has correct structure")
    void forbiddenStructure() throws Exception {
      Notification notification =
          createNotification(
              NOTIFICATION_ID,
              OTHER_CANDIDATE_ID,
              UUID.randomUUID(),
              "UNDER_REVIEW",
              DeliveryStatus.SENT);

      when(notificationRepository.findByNotificationId(NOTIFICATION_ID))
          .thenReturn(Optional.of(notification));

      mvc.perform(
              get("/api/v1/notifications/{id}", NOTIFICATION_ID)
                  .header("Authorization", "Bearer " + TOKEN))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Access Denied"))
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(
              jsonPath("$.detail").value("You do not have permission to access this notification"))
          .andExpect(jsonPath("$.instance").exists());
    }
  }
}
