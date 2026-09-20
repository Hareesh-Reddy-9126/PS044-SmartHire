package com.smarthire.auth.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.auth.config.SecurityConfig;
import com.smarthire.auth.service.AuthService;
import com.smarthire.auth.service.AuthTokens;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice authorization test for {@link AuthController}, loading the real {@link SecurityConfig}
 * (filter chain, {@code roles}→authority converter, {@code @PreAuthorize}, RFC 9457 problem
 * responses) over a mocked {@link JwtDecoder}. Because the decoder is the only mock, the full
 * token→role→decision path runs for real, so this is the day-to-day guard for the demonstration
 * authorization rules (decision 6): {@code /me} for any authenticated caller, {@code /admin/whoami}
 * for ADMIN only, and the 401/403 problem+json bodies. The full-stack variant is {@link
 * com.smarthire.auth.AuthApiIT} (skipped without Docker).
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerWebMvcTest {

  private static final String ADMIN_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USER_ID = "22222222-2222-2222-2222-222222222222";

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private AuthService authService;

  private static Jwt jwt(String subject, List<String> roles) {
    return Jwt.withTokenValue("token-" + subject)
        .header("alg", "RS256")
        .subject(subject)
        .claim("roles", roles)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900))
        .build();
  }

  @Test
  void meReturns200ForAnyAuthenticatedUser() throws Exception {
    when(jwtDecoder.decode("candidate-token")).thenReturn(jwt(USER_ID, List.of("CANDIDATE")));
    when(authService.me(UUID.fromString(USER_ID)))
        .thenReturn(
            new AuthService.Profile(
                UUID.fromString(USER_ID), "ada@example.com", List.of("CANDIDATE"), null));

    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer candidate-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sub").value(USER_ID))
        .andExpect(jsonPath("$.email").value("ada@example.com"))
        .andExpect(jsonPath("$.roles[0]").value("CANDIDATE"));
  }

  @Test
  void meReturns401ProblemJsonWithoutAToken() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void adminWhoamiReturns200ForAnAdmin() throws Exception {
    when(jwtDecoder.decode("admin-token")).thenReturn(jwt(ADMIN_ID, List.of("ADMIN")));
    when(authService.me(UUID.fromString(ADMIN_ID)))
        .thenReturn(
            new AuthService.Profile(
                UUID.fromString(ADMIN_ID), "admin@example.com", List.of("ADMIN"), null));
    when(authService.isAdmin(UUID.fromString(ADMIN_ID))).thenReturn(true);

    mvc.perform(get("/api/v1/auth/admin/whoami").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
  }

  @Test
  void demotedAdminIsRejectedDespiteAnOldAdminToken() throws Exception {
    when(jwtDecoder.decode("admin-token")).thenReturn(jwt(ADMIN_ID, List.of("ADMIN")));
    when(authService.isAdmin(UUID.fromString(ADMIN_ID))).thenReturn(false);

    mvc.perform(get("/api/v1/auth/admin/whoami").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginReturnsAccessTokenAndSecureRefreshCookie() throws Exception {
    when(authService.login("ada@example.com", "secret-password"))
        .thenReturn(new AuthTokens("access", Instant.now().plusSeconds(900), "refresh"));

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ada@example.com\",\"password\":\"secret-password\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access"))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                    .contains("HttpOnly", "Secure", "SameSite=Strict"));
  }

  @Test
  void oversizedLoginEmailIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"" + "a".repeat(256) + "@example.com\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void adminWhoamiReturns403ProblemJsonForANonAdmin() throws Exception {
    when(jwtDecoder.decode("candidate-token")).thenReturn(jwt(USER_ID, List.of("CANDIDATE")));

    mvc.perform(get("/api/v1/auth/admin/whoami").header("Authorization", "Bearer candidate-token"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void adminWhoamiReturns401ProblemJsonWithoutAToken() throws Exception {
    mvc.perform(get("/api/v1/auth/admin/whoami"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));
  }
}
