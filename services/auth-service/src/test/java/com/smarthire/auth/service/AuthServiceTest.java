package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smarthire.auth.domain.Role;
import com.smarthire.auth.domain.User;
import com.smarthire.auth.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication use-cases with collaborators mocked. Pins the security-relevant behaviours:
 * self-registration is always a CANDIDATE with a normalized email and a hashed password (decision
 * 7); duplicate registration and bad logins fail with generic, non-enumerable messages; refresh
 * rotates the token; logout both revokes the refresh family and denylists the access {@code jti}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtIssuer jwtIssuer;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private RevocationService revocationService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            userRepository,
            passwordEncoder,
            jwtIssuer,
            refreshTokenService,
            revocationService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void registerCreatesACandidateWithNormalizedEmailAndHashedPassword() {
    when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
    when(passwordEncoder.encode("secret-password")).thenReturn("HASHED");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID id = service.register("  Ada@Example.COM  ", "secret-password");

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.CANDIDATE);
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASHED");
    assertThat(saved.getValue().getOrgId()).isNull();
    assertThat(id).isEqualTo(saved.getValue().getId());
  }

  @Test
  void registerRejectsADuplicateEmail() {
    when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register("Ada@Example.com", "secret-password"))
        .isInstanceOf(EmailAlreadyExistsException.class)
        .hasMessage("Email already registered");

    verify(userRepository, never()).save(any());
  }

  @Test
  void loginRejectsAnUnknownEmailWithoutRevealingIt() {
    when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("ghost@example.com", "whatever-password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");
  }

  @Test
  void loginRejectsAWrongPasswordWithTheSameGenericMessage() {
    User user = new User(UUID.randomUUID(), "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW);
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "HASHED")).thenReturn(false);

    assertThatThrownBy(() -> service.login("ada@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");

    verifyNoInteractions(jwtIssuer);
  }

  @Test
  void loginIssuesAccessAndRefreshTokensForValidCredentials() {
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW);
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret-password", "HASHED")).thenReturn(true);
    Instant exp = NOW.plusSeconds(900);
    when(jwtIssuer.issue(userId, List.of("CANDIDATE"), null))
        .thenReturn(new IssuedToken("access-jwt", "jti", exp));
    when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

    AuthTokens tokens = service.login("ada@example.com", "secret-password");

    assertThat(tokens.accessToken()).isEqualTo("access-jwt");
    assertThat(tokens.accessTokenExpiresAt()).isEqualTo(exp);
    assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void meReturnsTheLiveProfileFromTheDatabase() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    User user = new User(userId, "ada@example.com", "HASHED", Role.RECRUITER, orgId, NOW);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    AuthService.Profile profile = service.me(userId);

    assertThat(profile.userId()).isEqualTo(userId);
    assertThat(profile.email()).isEqualTo("ada@example.com");
    assertThat(profile.roles()).containsExactly("RECRUITER");
    assertThat(profile.orgId()).isEqualTo(orgId);
  }

  @Test
  void meRejectsAnUnknownUser() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.me(userId))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Unknown user");
  }

  @Test
  void refreshRotatesTheTokenAndIssuesANewAccessToken() {
    UUID userId = UUID.randomUUID();
    when(refreshTokenService.rotate("old-refresh"))
        .thenReturn(new RefreshTokenService.RotationResult(userId, "new-refresh"));
    User user = new User(userId, "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    Instant exp = NOW.plusSeconds(900);
    when(jwtIssuer.issue(userId, List.of("CANDIDATE"), null))
        .thenReturn(new IssuedToken("access-2", "jti-2", exp));

    AuthTokens tokens = service.refresh("old-refresh");

    assertThat(tokens.accessToken()).isEqualTo("access-2");
    assertThat(tokens.accessTokenExpiresAt()).isEqualTo(exp);
    assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
  }

  @Test
  void logoutRevokesTheRefreshFamilyAndDenylistsTheAccessJti() {
    Instant exp = NOW.plusSeconds(900);

    service.logout("refresh-token", "access-jti", exp);

    verify(refreshTokenService).revoke("refresh-token");
    verify(revocationService).revoke("access-jti", exp);
  }

  @Test
  void logoutWithoutARefreshTokenStillDenylistsTheAccessJti() {
    Instant exp = NOW.plusSeconds(900);

    service.logout("   ", "access-jti", exp);

    verify(refreshTokenService, never()).revoke(anyString());
    verify(revocationService).revoke("access-jti", exp);
  }
}
