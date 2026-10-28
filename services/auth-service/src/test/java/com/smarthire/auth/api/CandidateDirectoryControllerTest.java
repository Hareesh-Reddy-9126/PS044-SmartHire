package com.smarthire.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.auth.config.SecurityConfig;
import com.smarthire.auth.service.AuthService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CandidateDirectoryController.class)
@Import(SecurityConfig.class)
class CandidateDirectoryControllerTest {

  private static final UUID CANDIDATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SECOND_CANDIDATE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private AuthService authService;

  private static Jwt jwt(String role) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("caller")
        .claim("roles", List.of(role))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900))
        .build();
  }

  private static String body(UUID... ids) {
    String values =
        java.util.Arrays.stream(ids)
            .map(id -> "\"" + id + "\"")
            .collect(java.util.stream.Collectors.joining(","));
    return "{\"candidateIds\":[" + values + "]}";
  }

  @Test
  void returnsCandidateEmailsForARecruiter() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt("RECRUITER"));
    when(authService.candidateEmails(any()))
        .thenReturn(
            Map.of(
                CANDIDATE_ID, "ada@example.com",
                SECOND_CANDIDATE_ID, "grace@example.com"));

    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CANDIDATE_ID, SECOND_CANDIDATE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].candidateId").value(CANDIDATE_ID.toString()))
        .andExpect(jsonPath("$[0].email").value("ada@example.com"))
        .andExpect(jsonPath("$[1].candidateId").value(SECOND_CANDIDATE_ID.toString()))
        .andExpect(jsonPath("$[1].email").value("grace@example.com"));
  }

  @Test
  void returnsCandidateEmailsForAnAdministrator() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt("ADMIN"));
    when(authService.candidateEmails(any())).thenReturn(Map.of(CANDIDATE_ID, "ada@example.com"));

    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CANDIDATE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("ada@example.com"));
  }

  @Test
  void omitsIdsTheServiceDidNotResolve() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt("RECRUITER"));
    when(authService.candidateEmails(any())).thenReturn(Map.of(CANDIDATE_ID, "ada@example.com"));

    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CANDIDATE_ID, SECOND_CANDIDATE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].candidateId").value(CANDIDATE_ID.toString()));
  }

  @Test
  void rejectsACandidateCaller() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt("CANDIDATE"));

    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CANDIDATE_ID)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAnonymousCallers() throws Exception {
    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(CANDIDATE_ID)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsAnEmptyBatch() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt("RECRUITER"));

    mvc.perform(
            post("/api/v1/users/candidates/emails")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[]}"))
        .andExpect(status().isBadRequest());
  }
}
