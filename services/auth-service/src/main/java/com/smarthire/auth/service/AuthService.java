package com.smarthire.auth.service;

import com.smarthire.auth.domain.Role;
import com.smarthire.auth.domain.User;
import com.smarthire.auth.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use-cases (register, login, refresh, logout). Public self-registration creates
 * {@code CANDIDATE} accounts only (decision 7); RECRUITER/ADMIN are provisioned by the dev seeder.
 * Access tokens are RS256 JWTs (15m); refresh tokens are rotated opaque tokens (7d).
 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtIssuer jwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final RevocationService revocationService;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtIssuer jwtIssuer,
      RefreshTokenService refreshTokenService,
      RevocationService revocationService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtIssuer = jwtIssuer;
    this.refreshTokenService = refreshTokenService;
    this.revocationService = revocationService;
    this.clock = clock;
  }

  /** Public self-registration — always a CANDIDATE (decision 7). Returns the new user id. */
  @Transactional
  public UUID register(String email, String rawPassword) {
    String normalized = email.trim().toLowerCase();
    if (userRepository.existsByEmail(normalized)) {
      throw new EmailAlreadyExistsException("Email already registered");
    }
    User user =
        new User(
            UUID.randomUUID(),
            normalized,
            passwordEncoder.encode(rawPassword),
            Role.CANDIDATE,
            null,
            Instant.now(clock));
    return userRepository.save(user).getId();
  }

  @Transactional
  public AuthTokens login(String email, String rawPassword) {
    User user =
        userRepository
            .findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
    return issueFor(user);
  }

  @Transactional
  public AuthTokens refresh(String refreshToken) {
    RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(refreshToken);
    User user =
        userRepository
            .findById(rotation.userId())
            .orElseThrow(() -> new InvalidTokenException("Unknown user for refresh token"));
    IssuedToken access = jwtIssuer.issue(user.getId(), roles(user), user.getOrgId());
    return new AuthTokens(access.value(), access.expiresAt(), rotation.refreshToken());
  }

  /**
   * Logout: revokes the refresh-token family and denylists the current access token's {@code jti}
   * until its natural expiry, so neither can be used again.
   */
  @Transactional
  public void logout(String refreshToken, String accessJti, Instant accessExpiresAt) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenService.revoke(refreshToken);
    }
    revocationService.revoke(accessJti, accessExpiresAt);
  }

  /**
   * Current identity of an authenticated caller, resolved from the live database record by the JWT
   * subject. Backs {@code /me} and {@code /admin/whoami} — email is not a JWT claim, so it must be
   * looked up here rather than read from the token.
   */
  @Transactional(readOnly = true)
  public Profile me(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new InvalidTokenException("Unknown user"));
    return new Profile(user.getId(), user.getEmail(), roles(user), user.getOrgId());
  }

  private AuthTokens issueFor(User user) {
    IssuedToken access = jwtIssuer.issue(user.getId(), roles(user), user.getOrgId());
    String refresh = refreshTokenService.issue(user.getId());
    return new AuthTokens(access.value(), access.expiresAt(), refresh);
  }

  private static List<String> roles(User user) {
    return List.of(user.getRole().name());
  }

  /** Service-layer identity view (the api layer maps it to {@code MeResponse}). */
  public record Profile(UUID userId, String email, List<String> roles, UUID orgId) {

    public Profile {
      roles = roles == null ? null : List.copyOf(roles);
    }
  }
}
