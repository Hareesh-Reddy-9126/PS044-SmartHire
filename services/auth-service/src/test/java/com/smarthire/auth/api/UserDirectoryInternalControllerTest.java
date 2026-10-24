package com.smarthire.auth.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.auth.config.SecurityConfig;
import com.smarthire.auth.service.AuthService;
import com.smarthire.auth.service.UserNotFoundException;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(UserDirectoryInternalController.class)
@Import(SecurityConfig.class)
class UserDirectoryInternalControllerTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private AuthService authService;

  private static Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("service-caller")
        .claim("roles", List.of("RECRUITER"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900))
        .build();
  }

  @Test
  void returnsTheUsersEmailForAnAuthenticatedCaller() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt());
    when(authService.emailOf(USER_ID)).thenReturn("candidate@example.com");

    mvc.perform(
            get("/internal/v1/users/{userId}/email", USER_ID)
                .header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.email").value("candidate@example.com"));
  }

  @Test
  void reportsAnUnknownUserAs404() throws Exception {
    when(jwtDecoder.decode("token")).thenReturn(jwt());
    when(authService.emailOf(USER_ID))
        .thenThrow(new UserNotFoundException("User not found: " + USER_ID));

    mvc.perform(
            get("/internal/v1/users/{userId}/email", USER_ID)
                .header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("User not found"));
  }

  @Test
  void rejectsAnonymousCallers() throws Exception {
    mvc.perform(get("/internal/v1/users/{userId}/email", USER_ID))
        .andExpect(status().isUnauthorized());
  }
}
