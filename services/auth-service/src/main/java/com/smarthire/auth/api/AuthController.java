package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.LoginRequest;
import com.smarthire.auth.api.dto.MeResponse;
import com.smarthire.auth.api.dto.RegisterRequest;
import com.smarthire.auth.api.dto.RegisterResponse;
import com.smarthire.auth.api.dto.TokenResponse;
import com.smarthire.auth.service.AuthService;
import com.smarthire.auth.service.AuthTokens;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication API (Inc 1). Registration, login and refresh are public (they run before or after
 * a valid access token exists); logout and the identity endpoints require authentication. Role
 * gating for the ADMIN-only demonstration endpoint is enforced with {@code @PreAuthorize} (decision
 * 6). Service results are mapped to response DTOs here so the service layer stays free of the api
 * layer (governance §3).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String BEARER = "Bearer";
  static final String REFRESH_COOKIE = "smarthire_refresh";
  private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    UUID userId = authService.register(request.email(), request.password());
    return new RegisterResponse(userId.toString());
  }

  @PostMapping("/login")
  public TokenResponse login(
      @Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletResponse response) {
    AuthTokens tokens = authService.login(request.email(), request.password());
    addRefreshCookie(response, tokens.refreshToken());
    return toTokenResponse(tokens);
  }

  @PostMapping("/refresh")
  public TokenResponse refresh(
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
      jakarta.servlet.http.HttpServletResponse response) {
    AuthTokens tokens = authService.refresh(refreshToken);
    addRefreshCookie(response, tokens.refreshToken());
    return toTokenResponse(tokens);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @AuthenticationPrincipal Jwt accessToken,
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
      jakarta.servlet.http.HttpServletResponse response) {
    authService.logout(refreshToken, accessToken.getId(), accessToken.getExpiresAt());
    clearRefreshCookie(response);
  }

  /** Any authenticated caller (decision 6). */
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Jwt accessToken) {
    return toMeResponse(accessToken);
  }

  /** ADMIN only (decision 6) — demonstrates role-gated authorization. */
  @GetMapping("/admin/whoami")
  @PreAuthorize("hasRole('ADMIN')")
  public MeResponse adminWhoami(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = UUID.fromString(accessToken.getSubject());
    if (!authService.isAdmin(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("Forbidden");
    }
    return toMeResponse(accessToken);
  }

  private static void addRefreshCookie(
      jakarta.servlet.http.HttpServletResponse response, String refreshToken) {
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(REFRESH_COOKIE_TTL)
            .build()
            .toString());
  }

  private static void clearRefreshCookie(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
  }

  private MeResponse toMeResponse(Jwt accessToken) {
    AuthService.Profile profile = authService.me(UUID.fromString(accessToken.getSubject()));
    return new MeResponse(
        profile.userId().toString(),
        profile.email(),
        profile.roles(),
        profile.orgId() == null ? null : profile.orgId().toString());
  }

  private static TokenResponse toTokenResponse(AuthTokens tokens) {
    return new TokenResponse(BEARER, tokens.accessToken(), tokens.accessTokenExpiresAt());
  }
}
