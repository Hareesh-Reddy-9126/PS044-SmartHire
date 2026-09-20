package com.smarthire.auth.service;

import com.smarthire.auth.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues RS256 access tokens (ADR-0009). Claims: {@code sub} (user id), {@code roles}, optional
 * {@code orgId}, {@code iss}, {@code aud}, {@code exp}, {@code iat}, {@code jti}.
 * Offline-verifiable via JWKS. Access-token TTL is the approved 15 minutes (decision 2). The
 * signing {@code kid} header is populated automatically by the encoder from the single JWK in the
 * key source.
 */
@Service
public class JwtIssuer {

  private final JwtEncoder encoder;
  private final JwtProperties props;
  private final Clock clock;

  public JwtIssuer(JwtEncoder encoder, JwtProperties props, Clock clock) {
    this.encoder = encoder;
    this.props = props;
    this.clock = clock;
  }

  public IssuedToken issue(UUID userId, List<String> roles, UUID orgId) {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(props.accessTokenTtl());
    String jti = UUID.randomUUID().toString();

    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .audience(List.of(props.audience()))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(jti)
            .claim("roles", roles);
    if (orgId != null) {
      claims.claim("orgId", orgId.toString());
    }

    String value =
        encoder
            .encode(
                JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims.build()))
            .getTokenValue();
    return new IssuedToken(value, jti, expiresAt);
  }
}
