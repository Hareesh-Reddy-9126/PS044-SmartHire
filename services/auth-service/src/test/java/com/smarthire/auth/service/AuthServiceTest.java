package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smarthire.auth.domain.Organization;
import com.smarthire.auth.domain.Role;
import com.smarthire.auth.domain.User;
import com.smarthire.auth.infra.OrganizationRepository;
import com.smarthire.auth.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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
  @Mock private OrganizationRepository organizationRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtIssuer jwtIssuer;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private RevocationService revocationService;
  @Mock private RegistrationPolicy registrationPolicy;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            userRepository,
            organizationRepository,
            passwordEncoder,
            jwtIssuer,
            refreshTokenService,
            revocationService,
            registrationPolicy,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void registerCreatesACandidateWithNormalizedEmailAndHashedPassword() {
    when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
    when(passwordEncoder.encode("secret-password")).thenReturn("HASHED");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID id = service.register("  Ada@Example.COM  ", "secret-password");

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
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

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void registerTranslatesAConcurrentDuplicateEmail() {
    when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
    when(passwordEncoder.encode("secret-password")).thenReturn("HASHED");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    assertThatThrownBy(() -> service.register("Ada@Example.com", "secret-password"))
        .isInstanceOf(EmailAlreadyExistsException.class)
        .hasMessage("Email already registered");
  }

  @Test
  void recruiterRegistrationCreatesAnOwnedOrganization() {
    when(userRepository.existsByEmail("hire@acme.example")).thenReturn(false);
    when(passwordEncoder.encode("secret-password")).thenReturn("HASHED");
    when(organizationRepository.saveAndFlush(any(Organization.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.register(
        "hire@acme.example", "secret-password", Role.RECRUITER, "  Acme   Corp  ", null);

    ArgumentCaptor<Organization> organization = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).saveAndFlush(organization.capture());
    assertThat(organization.getValue().getName()).isEqualTo("Acme Corp");
    assertThat(organization.getValue().getCreatedAt()).isEqualTo(NOW);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(Role.RECRUITER);
    assertThat(saved.getValue().getOrgId()).isEqualTo(organization.getValue().getId());
  }

  @Test
  void recruiterRegistrationRequiresAnOrganizationName() {
    assertThatThrownBy(
            () ->
                service.register(
                    "hire@acme.example", "secret-password", Role.RECRUITER, "   ", null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("An organization name is required for recruiter registration.");

    verify(organizationRepository, never()).saveAndFlush(any(Organization.class));
    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void candidateRegistrationRejectsAnOrganizationName() {
    assertThatThrownBy(
            () ->
                service.register(
                    "ada@example.com", "secret-password", Role.CANDIDATE, "Acme Corp", null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("An organization name is only accepted for recruiter registration.");

    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void adminRegistrationRequiresAValidInvitationCode() {
    when(registrationPolicy.isAdminInviteCodeValid("wrong-code")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.register(
                    "ops@smarthire.example", "secret-password", Role.ADMIN, null, "wrong-code"))
        .isInstanceOf(AdminRegistrationDeniedException.class)
        .hasMessage("Administrator registration requires a valid invitation code.");

    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void adminRegistrationCreatesAnAccountWithoutAnOrganizationWhenTheCodeMatches() {
    when(registrationPolicy.isAdminInviteCodeValid("deployment-code")).thenReturn(true);
    when(userRepository.existsByEmail("ops@smarthire.example")).thenReturn(false);
    when(passwordEncoder.encode("secret-password")).thenReturn("HASHED");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.register(
        "ops@smarthire.example", "secret-password", Role.ADMIN, null, "deployment-code");

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.getValue().getOrgId()).isNull();
    verifyNoInteractions(organizationRepository);
  }

  @Test
  void loginRejectsAnUnknownEmailWithoutRevealingIt() {
    when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("whatever-password", AuthService.DUMMY_PASSWORD_HASH))
        .thenReturn(false);

    assertThatThrownBy(() -> service.login("ghost@example.com", "whatever-password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");

    verify(passwordEncoder).matches("whatever-password", AuthService.DUMMY_PASSWORD_HASH);
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
  void googleLoginKeepsTheExistingRoleAndIssuesTokens() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    User user = new User(userId, "ada@example.com", "HASHED", Role.RECRUITER, orgId, NOW);
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
    Instant exp = NOW.plusSeconds(900);
    when(jwtIssuer.issue(userId, List.of("RECRUITER"), orgId))
        .thenReturn(new IssuedToken("access-jwt", "jti", exp));
    when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

    AuthTokens tokens = service.loginWithGoogle("  Ada@Example.com ");

    assertThat(tokens.accessToken()).isEqualTo("access-jwt");
    assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    verify(userRepository, never()).saveAndFlush(any(User.class));
  }

  @Test
  void googleLoginCreatesACandidateForAPreviouslyUnseenEmail() {
    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("RANDOM-HASH");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(jwtIssuer.issue(any(UUID.class), any(), any()))
        .thenReturn(new IssuedToken("access-jwt", "jti", NOW.plusSeconds(900)));
    when(refreshTokenService.issue(any(UUID.class))).thenReturn("refresh-token");

    service.loginWithGoogle("New@Example.com");

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.CANDIDATE);
    assertThat(saved.getValue().getOrgId()).isNull();
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("RANDOM-HASH");
  }

  @Test
  void googleLoginUsesTheAccountThatWonAConcurrentCreationRace() {
    UUID userId = UUID.randomUUID();
    User existing = new User(userId, "new@example.com", "HASHED", Role.CANDIDATE, null, NOW);
    when(userRepository.findByEmail("new@example.com"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(passwordEncoder.encode(anyString())).thenReturn("RANDOM-HASH");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));
    when(jwtIssuer.issue(userId, List.of("CANDIDATE"), null))
        .thenReturn(new IssuedToken("access-jwt", "jti", NOW.plusSeconds(900)));
    when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

    AuthTokens tokens = service.loginWithGoogle("new@example.com");

    assertThat(tokens.accessToken()).isEqualTo("access-jwt");
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
  void isAdminReadsTheCurrentDatabaseRole() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId))
        .thenReturn(
            Optional.of(new User(userId, "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW)));

    assertThat(service.isAdmin(userId)).isFalse();
  }

  @Test
  void emailOfReturnsTheStoredAddress() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId))
        .thenReturn(
            Optional.of(new User(userId, "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW)));

    assertThat(service.emailOf(userId)).isEqualTo("ada@example.com");
  }

  @Test
  void emailOfRejectsAnUnknownUser() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.emailOf(userId))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessage("User not found: " + userId);
  }

  @Test
  void candidateEmailsReturnsOnlyCandidateAccounts() {
    UUID candidateId = UUID.randomUUID();
    UUID recruiterId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    when(userRepository.findAllById(List.of(candidateId, recruiterId, adminId)))
        .thenReturn(
            List.of(
                new User(candidateId, "ada@example.com", "HASHED", Role.CANDIDATE, null, NOW),
                new User(recruiterId, "recruiter@example.com", "HASHED", Role.RECRUITER, null, NOW),
                new User(adminId, "admin@example.com", "HASHED", Role.ADMIN, null, NOW)));

    Map<UUID, String> emails = service.candidateEmails(List.of(candidateId, recruiterId, adminId));

    assertThat(emails).containsExactly(Map.entry(candidateId, "ada@example.com"));
  }

  @Test
  void candidateEmailsReturnsEmptyForAnEmptyRequest() {
    assertThat(service.candidateEmails(List.of())).isEmpty();
    verifyNoInteractions(userRepository);
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

  @Test
  void logoutWithoutAnAccessTokenStillRevokesTheRefreshFamily() {
    service.logout("refresh-token", null, null);

    verify(refreshTokenService).revoke("refresh-token");
    verify(revocationService, never()).revoke(anyString(), any(Instant.class));
  }

  @Test
  void refreshTransactionDoesNotRollbackOnInvalidTokenException() throws Exception {
    Transactional annotation =
        AuthService.class.getMethod("refresh", String.class).getAnnotation(Transactional.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.noRollbackFor()).contains(InvalidTokenException.class);
  }

  @Test
  void refreshPropagatesReuseFailure() {
    when(refreshTokenService.rotate("reused-token"))
        .thenThrow(new InvalidTokenException("Refresh token reuse detected"));

    assertThatThrownBy(() -> service.refresh("reused-token"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Refresh token reuse detected");
  }
}
