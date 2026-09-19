package com.smarthire.auth.service;

import com.smarthire.auth.config.JwtProperties;
import com.smarthire.auth.domain.RefreshToken;
import com.smarthire.auth.infra.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation + reuse-detection (ADR-0009). The raw token is a high-entropy
 * random string returned to the client; only its SHA-256 hash is persisted (governance §17). Each
 * refresh rotates the token: the presented one is revoked and a new one issued in the same family.
 * Presenting an already-revoked token is treated as theft — the whole family is revoked, so a
 * stolen-then-rotated token cannot be used. Refresh-token TTL is the approved 7 days (decision 3).
 */
@Service
public class RefreshTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository repository;
  private final RefreshTokenFamilyRevocationService familyRevocationService;
  private final JwtProperties props;
  private final Clock clock;

  public RefreshTokenService(
      RefreshTokenRepository repository,
      RefreshTokenFamilyRevocationService familyRevocationService,
      JwtProperties props,
      Clock clock) {
    this.repository = repository;
    this.familyRevocationService = familyRevocationService;
    this.props = props;
    this.clock = clock;
  }

  /** Issues a brand-new refresh token in a fresh family (login). Returns the raw token. */
  @Transactional
  public String issue(UUID userId) {
    return persistNew(userId, UUID.randomUUID()).rawToken();
  }

  /**
   * Rotates a presented refresh token. Reuse of a revoked token revokes its whole family.
   *
   * @throws InvalidTokenException if the token is unknown, expired, or already revoked (reuse)
   */
  @Transactional
  public RotationResult rotate(String rawToken) {
    RefreshToken current =
        repository
            .findByTokenHashForUpdate(sha256(rawToken))
            .orElseThrow(() -> new InvalidTokenException("Unknown refresh token"));

    if (current.isRevoked()) {
      // Reuse of a rotated/revoked token: assume compromise, revoke the entire family.
      familyRevocationService.revokeFamily(current.getFamilyId());
      throw new InvalidTokenException("Refresh token reuse detected");
    }
    if (current.isExpired(Instant.now(clock))) {
      throw new InvalidTokenException("Expired refresh token");
    }

    Persisted next = persistNew(current.getUserId(), current.getFamilyId());
    current.revoke(next.entity().getId());
    return new RotationResult(current.getUserId(), next.rawToken());
  }

  /** Revokes the family of a presented token (logout). Idempotent; unknown tokens are ignored. */
  @Transactional
  public void revoke(String rawToken) {
    repository
        .findByTokenHash(sha256(rawToken))
        .ifPresent(token -> repository.revokeFamily(token.getFamilyId()));
  }

  private Persisted persistNew(UUID userId, UUID familyId) {
    String rawToken = randomToken();
    Instant now = Instant.now(clock);
    RefreshToken entity =
        new RefreshToken(
            UUID.randomUUID(),
            userId,
            familyId,
            sha256(rawToken),
            now,
            now.plus(props.refreshTokenTtl()));
    return new Persisted(repository.save(entity), rawToken);
  }

  private static String randomToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Outcome of a rotation: the user to re-issue an access token for, and the new refresh token. */
  public record RotationResult(UUID userId, String refreshToken) {}

  private record Persisted(RefreshToken entity, String rawToken) {}
}
