package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.GoogleLoginRequest;
import com.smarthire.auth.api.dto.LoginRequest;
import com.smarthire.auth.api.dto.MeResponse;
import com.smarthire.auth.api.dto.RegisterRequest;
import com.smarthire.auth.api.dto.RegisterResponse;
import com.smarthire.auth.api.dto.TokenResponse;
import com.smarthire.auth.domain.Role;
import com.smarthire.auth.service.AuthService;
import com.smarthire.auth.service.AuthTokens;
import com.smarthire.auth.service.GoogleTokenVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(
    name = "Authentication",
    description = "User registration, login, token refresh, and identity endpoints")
public class AuthController {

  private static final String BEARER = "Bearer";
  static final String REFRESH_COOKIE = "smarthire_refresh";
  private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

  private final AuthService authService;
  private final GoogleTokenVerifier googleTokenVerifier;
  private final Environment environment;

  public AuthController(
      AuthService authService, GoogleTokenVerifier googleTokenVerifier, Environment environment) {
    this.authService = authService;
    this.googleTokenVerifier = googleTokenVerifier;
    this.environment = environment;
  }

  @Operation(
      summary = "Register a new user",
      description =
          "Registers a candidate, recruiter or administrator account. Candidates and recruiters "
              + "self-register; recruiters must name their organization and administrators must "
              + "present the deployment's invitation code. Public endpoint.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "User registered successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegisterResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed or a role-specific field is missing",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Administrator registration denied (missing or invalid invitation code)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "409",
            description = "Email already registered",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "")
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    // Bean validation has already constrained the value to the three self-service roles.
    Role role =
        request.role() == null
            ? null
            : Role.valueOf(request.role().trim().toUpperCase(Locale.ROOT));
    UUID userId =
        authService.register(
            request.email(),
            request.password(),
            role,
            request.organizationName(),
            request.adminInviteCode());
    return new RegisterResponse(userId.toString());
  }

  @Operation(
      summary = "User login",
      description =
          "Authenticates a user and returns access token with refresh token in HttpOnly cookie. Public endpoint.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "")
  @PostMapping("/login")
  public TokenResponse login(
      @Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletResponse response) {
    AuthTokens tokens = authService.login(request.email(), request.password());
    addRefreshCookie(response, tokens.refreshToken());
    return toTokenResponse(tokens);
  }

  @Operation(
      summary = "Sign in with Google",
      description =
          "Verifies a Google Identity Services ID token and returns an access token with the"
              + " refresh token in an HttpOnly cookie. New Google accounts are created as"
              + " candidates; existing accounts keep their current role. Public endpoint.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Google sign-in successful",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "The Google credential could not be verified",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "503",
            description = "Google sign-in is not configured for this deployment",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "")
  @PostMapping("/google")
  public TokenResponse google(
      @Valid @RequestBody GoogleLoginRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    String email = googleTokenVerifier.verifiedEmail(request.credential());
    AuthTokens tokens = authService.loginWithGoogle(email);
    addRefreshCookie(response, tokens.refreshToken());
    return toTokenResponse(tokens);
  }

  @Operation(
      summary = "Refresh access token",
      description =
          "Refreshes the access token using the refresh token from the HttpOnly cookie. Public endpoint.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired refresh token",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "")
  @PostMapping("/refresh")
  public TokenResponse refresh(
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
      jakarta.servlet.http.HttpServletResponse response) {
    AuthTokens tokens = authService.refresh(refreshToken);
    addRefreshCookie(response, tokens.refreshToken());
    return toTokenResponse(tokens);
  }

  @Operation(
      summary = "User logout",
      description =
          "Logs out the user by invalidating the refresh token and clearing the cookie. The"
              + " access token is optional so an expired access token cannot prevent the refresh"
              + " cookie from being cleared.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized (not returned for logout; kept for API compatibility)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @AuthenticationPrincipal Jwt accessToken,
      @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
      jakarta.servlet.http.HttpServletResponse response) {
    if (accessToken != null) {
      authService.logout(refreshToken, accessToken.getId(), accessToken.getExpiresAt());
    } else {
      authService.logout(refreshToken, null, null);
    }
    clearRefreshCookie(response);
  }

  /** Any authenticated caller (decision 6). */
  @Operation(
      summary = "Get current user profile",
      description = "Returns the profile of the authenticated user. Requires authentication.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profile retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MeResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Jwt accessToken) {
    return toMeResponse(accessToken);
  }

  /** ADMIN only (decision 6) — demonstrates role-gated authorization. */
  @Operation(
      summary = "Get admin profile",
      description = "Returns the profile of the authenticated admin user. Requires ADMIN role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Admin profile retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MeResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires ADMIN role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/whoami")
  @PreAuthorize("hasRole('ADMIN')")
  public MeResponse adminWhoami(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = UUID.fromString(accessToken.getSubject());
    if (!authService.isAdmin(userId)) {
      throw new org.springframework.security.access.AccessDeniedException("Forbidden");
    }
    return toMeResponse(accessToken);
  }

  private boolean secureRefreshCookie() {
    boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
    boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
    // A prod,local combination must never downgrade the cookie to plain HTTP.
    return prod || !local;
  }

  private void addRefreshCookie(
      jakarta.servlet.http.HttpServletResponse response, String refreshToken) {
    boolean secure = secureRefreshCookie();
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(REFRESH_COOKIE_TTL)
            .build()
            .toString());
  }

  private void clearRefreshCookie(jakarta.servlet.http.HttpServletResponse response) {
    boolean secure = secureRefreshCookie();
    response.setHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
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
