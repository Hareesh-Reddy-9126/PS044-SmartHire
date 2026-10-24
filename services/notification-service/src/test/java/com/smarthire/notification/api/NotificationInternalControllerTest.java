package com.smarthire.notification.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.common.contract.notification.ApplicationStatusChangeRequest;
import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.notification.domain.Notification;
import com.smarthire.notification.infra.UserDirectoryClient;
import com.smarthire.notification.security.SecurityConfig;
import com.smarthire.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** WebMvc tests for {@link NotificationInternalController}. */
@WebMvcTest(NotificationInternalController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "smarthire.notification.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.notification.jwt.issuer=https://smarthire.local/auth",
      "smarthire.notification.jwt.audience=smarthire"
    })
class NotificationInternalControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private UserDirectoryClient userDirectoryClient;
  @MockitoBean private JwtDecoder jwtDecoder;

  private static final UUID CANDIDATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID APPLICATION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String TOKEN = "test-token";

  private Jwt serviceJwt;

  @BeforeEach
  void setUp() {
    serviceJwt =
        Jwt.withTokenValue(TOKEN)
            .header("alg", "RS256")
            .subject("service-account")
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .claim("roles", List.of("SERVICE"))
            .build();

    when(jwtDecoder.decode(TOKEN)).thenReturn(serviceJwt);

    when(userDirectoryClient.getEmail(CANDIDATE_ID))
        .thenReturn(
            new UserDirectoryClient.UserEmailResponse(CANDIDATE_ID, "candidate@example.com"));

    when(notificationService.createAndSendNotification(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              UUID candidateId = inv.getArgument(0);
              UUID applicationId = inv.getArgument(1);
              String eventStatus = inv.getArgument(2);
              String recipientEmail = inv.getArgument(3);

              Notification n =
                  new Notification(
                      UUID.randomUUID(),
                      candidateId,
                      applicationId,
                      eventStatus,
                      recipientEmail,
                      "Test Subject",
                      "Test Message",
                      DeliveryStatus.SENT,
                      Instant.now(),
                      Instant.now(),
                      null);
              return n;
            });
  }

  @Nested
  @DisplayName("Valid internal notification request")
  class ValidRequest {

    @Test
    @DisplayName("Returns 202 Accepted with notification response")
    void accepted() throws Exception {
      ApplicationStatusChangeRequest request =
          new ApplicationStatusChangeRequest(CANDIDATE_ID, APPLICATION_ID, "UNDER_REVIEW");

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()))
          .andExpect(jsonPath("$.applicationId").value(APPLICATION_ID.toString()))
          .andExpect(jsonPath("$.eventStatus").value("UNDER_REVIEW"))
          .andExpect(jsonPath("$.recipientEmail").value("candidate@example.com"))
          .andExpect(jsonPath("$.deliveryStatus").value("SENT"));
    }

    @Test
    @DisplayName("Supported statuses are accepted")
    void supportedStatuses() throws Exception {
      for (String status : new String[] {"UNDER_REVIEW", "SHORTLISTED", "HIRED", "REJECTED"}) {
        ApplicationStatusChangeRequest request =
            new ApplicationStatusChangeRequest(CANDIDATE_ID, APPLICATION_ID, status);

        mvc.perform(
                post("/internal/v1/notifications/application-status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + TOKEN)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.eventStatus").value(status));
      }
    }
  }

  @Nested
  @DisplayName("Invalid request validation")
  class InvalidRequest {

    @Test
    @DisplayName("Missing candidateId returns 400")
    void missingCandidateId() throws Exception {
      String json =
          """
          {"applicationId": "%s", "status": "UNDER_REVIEW"}
          """
              .formatted(APPLICATION_ID);

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(json))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Missing applicationId returns 400")
    void missingApplicationId() throws Exception {
      String json =
          """
          {"candidateId": "%s", "status": "UNDER_REVIEW"}
          """
              .formatted(CANDIDATE_ID);

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(json))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Missing status returns 400")
    void missingStatus() throws Exception {
      String json =
          """
          {"candidateId": "%s", "applicationId": "%s"}
          """
              .formatted(CANDIDATE_ID, APPLICATION_ID);

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(json))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid UUID format returns 400")
    void invalidUuid() throws Exception {
      String json =
          """
          {"candidateId": "not-a-uuid", "applicationId": "%s", "status": "UNDER_REVIEW"}
          """
              .formatted(APPLICATION_ID);

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(json))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Service interaction")
  class ServiceInteraction {

    @Test
    @DisplayName("Service is called with correct parameters")
    void serviceCalledWithCorrectParams() throws Exception {
      ApplicationStatusChangeRequest request =
          new ApplicationStatusChangeRequest(CANDIDATE_ID, APPLICATION_ID, "HIRED");

      mvc.perform(
              post("/internal/v1/notifications/application-status")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("Authorization", "Bearer " + TOKEN)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isAccepted());

      ArgumentCaptor<ApplicationStatusChangeRequest> captor =
          ArgumentCaptor.forClass(ApplicationStatusChangeRequest.class);
      org.mockito.Mockito.verify(notificationService)
          .createAndSendNotification(
              org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
              org.mockito.ArgumentMatchers.eq(APPLICATION_ID),
              org.mockito.ArgumentMatchers.eq("HIRED"),
              org.mockito.ArgumentMatchers.anyString());
    }
  }
}
