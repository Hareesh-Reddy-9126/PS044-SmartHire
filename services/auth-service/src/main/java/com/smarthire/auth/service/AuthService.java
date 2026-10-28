package com.smarthire.auth.service;

import com.smarthire.auth.domain.Organization;
import com.smarthire.auth.domain.Role;
import com.smarthire.auth.domain.User;
import com.smarthire.auth.infra.OrganizationRepository;
import com.smarthire.auth.infra.UserRepository;
import com.smarthire.common.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use-cases (register, login, refresh, logout).
 *
 * <p>Self-registration is role-aware: candidates register openly, recruiters register with an
 * organization that owns their job postings, and administrators must present the deployment's
 * invitation code. Access tokens are RS256 JWTs (15m); refresh tokens are rotated opaque tokens
 * (7d).
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  static final String DUMMY_PASSWORD_HASH =
      "{bcrypt}$2a$12$aIBWy02mSzSW3ybBC41KCOHoLO.xaC69G3HyqXv1PTGsOrfGKfeZG";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtIssuer jwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final RevocationService revocationService;
  private final RegistrationPolicy registrationPolicy;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      PasswordEncoder passwordEncoder,
      JwtIssuer jwtIssuer,
      RefreshTokenService refreshTokenService,
      RevocationService revocationService,
      RegistrationPolicy registrationPolicy,
      Clock clock) {
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtIssuer = jwtIssuer;
    this.refreshTokenService = refreshTokenService;
    this.revocationService = revocationService;
    this.registrationPolicy = registrationPolicy;
    this.clock = clock;
  }

  /** Candidate self-registration. Retained for callers that predate role-aware registration. */
  @Transactional
  public UUID register(String email, String rawPassword) {
    return register(email, rawPassword, Role.CANDIDATE, null, null);
  }

  /**
   * Role-aware self-registration. Returns the new user id.
   *
   * <p>A recruiter account is created with a freshly created organization, so ownership checks in
   * job-service work on the very first request. Administrator accounts are created only when the
   * deployment has configured an invitation code and the request presents it correctly.
   *
   * @param role requested role; {@code null} keeps the original candidate-only behaviour
   * @param organizationName required for {@code RECRUITER}
   * @param adminInviteCode required for {@code ADMIN}
   * @throws InvalidRegistrationException when a role-specific field is missing or invalid
   * @throws AdminRegistrationDeniedException when administrator registration is not authorised
   * @throws EmailAlreadyExistsException when the email is already registered
   */
  @Transactional
  public UUID register(
      String email,
      String rawPassword,
      Role role,
      String organizationName,
      String adminInviteCode) {
    Role requestedRole = role == null ? Role.CANDIDATE : role;
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    UUID orgId = resolveOrganizationId(requestedRole, organizationName, normalizedEmail);
    if (requestedRole == Role.ADMIN
        && !registrationPolicy.isAdminInviteCodeValid(adminInviteCode)) {
      AuditService.auth(
          AuditService.AuthEvent.REGISTER,
          null,
          normalizedEmail,
          AuditService.Outcome.FAILURE,
          "Administrator invitation code rejected");
      throw new AdminRegistrationDeniedException();
    }

    if (userRepository.existsByEmail(normalizedEmail)) {
      AuditService.auth(
          AuditService.AuthEvent.REGISTER,
          null,
          normalizedEmail,
          AuditService.Outcome.FAILURE,
          "Email already exists");
      throw new EmailAlreadyExistsException("Email already registered");
    }

    User user =
        new User(
            UUID.randomUUID(),
            normalizedEmail,
            passwordEncoder.encode(rawPassword),
            requestedRole,
            orgId,
            Instant.now(clock));
    try {
      UUID userId = userRepository.saveAndFlush(user).getId();
      AuditService.auth(
          AuditService.AuthEvent.REGISTER,
          userId,
          normalizedEmail,
          AuditService.Outcome.SUCCESS,
          "role=" + requestedRole);
      log.info("Registered {} account {}", requestedRole, userId);
      return userId;
    } catch (DataIntegrityViolationException ex) {
      AuditService.auth(
          AuditService.AuthEvent.REGISTER,
          null,
          normalizedEmail,
          AuditService.Outcome.FAILURE,
          "Email already exists");
      throw new EmailAlreadyExistsException("Email already registered");
    }
  }

  @Transactional
  public AuthTokens login(String email, String rawPassword) {
    User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
    String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (!passwordEncoder.matches(rawPassword, passwordHash) || user == null) {
      AuditService.auth(
          AuditService.AuthEvent.LOGIN_FAILURE,
          null,
          normalizedEmail,
          AuditService.Outcome.FAILURE,
          "Invalid credentials");
      throw new InvalidCredentialsException("Invalid email or password");
    }
    AuditService.auth(
        AuditService.AuthEvent.LOGIN_SUCCESS,
        user.getId(),
        normalizedEmail,
        AuditService.Outcome.SUCCESS,
        null);
    return issueFor(user);
  }

  @Transactional(noRollbackFor = InvalidTokenException.class)
  public AuthTokens refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      AuditService.auth(
          AuditService.AuthEvent.REFRESH,
          null,
          null,
          AuditService.Outcome.FAILURE,
          "Missing refresh token");
      throw new InvalidTokenException("Missing refresh token");
    }
    RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(refreshToken);
    User user =
        userRepository
            .findById(rotation.userId())
            .orElseThrow(() -> new InvalidTokenException("Unknown user for refresh token"));
    AuditService.auth(
        AuditService.AuthEvent.REFRESH,
        user.getId(),
        user.getEmail(),
        AuditService.Outcome.SUCCESS,
        null);
    IssuedToken access = jwtIssuer.issue(user.getId(), roles(user), user.getOrgId());
    return new AuthTokens(access.value(), access.expiresAt(), rotation.refreshToken());
  }

  /**
   * Logout: revokes the refresh-token family and denylists the current access token's {@code jti}
   * until its natural expiry, so neither can be used again.
   */
  @Transactional
  public void logout(String refreshToken, String accessJti, Instant accessExpiresAt) {
    // We don't have the user ID here directly; the access token JTI is revoked.
    // The audit event will be logged with the JTI as detail for traceability.
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenService.revoke(refreshToken);
    }
    revocationService.revoke(accessJti, accessExpiresAt);
    AuditService.auth(
        AuditService.AuthEvent.LOGOUT,
        null,
        null,
        AuditService.Outcome.SUCCESS,
        "accessJti=" + accessJti);
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

  @Transactional(readOnly = true)
  public boolean isAdmin(UUID userId) {
    return userRepository.findById(userId).map(user -> user.getRole() == Role.ADMIN).orElse(false);
  }

  /**
   * Resolves the current email for a user id. Backs the internal user-directory endpoint used by
   * notification-service, which must address candidate notifications to a real mailbox rather than
   * a value derived from the id.
   *
   * @throws UserNotFoundException when no such user exists
   */
  @Transactional(readOnly = true)
  public String emailOf(UUID userId) {
    return userRepository
        .findById(userId)
        .map(User::getEmail)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
  }

  /**
   * Resolves email addresses for the requested candidate ids.
   *
   * <p>Backs the recruiter-facing candidate directory. Only {@link Role#CANDIDATE} accounts are
   * returned; unknown ids, recruiters and administrators are omitted so this cannot be used as a
   * general user directory.
   */
  @Transactional(readOnly = true)
  public Map<UUID, String> candidateEmails(Collection<UUID> candidateIds) {
    if (candidateIds == null || candidateIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllById(candidateIds).stream()
        .filter(user -> user.getRole() == Role.CANDIDATE)
        .collect(Collectors.toMap(User::getId, User::getEmail));
  }

  /**
   * Creates the organization that owns a new recruiter's postings. Candidate and administrator
   * accounts do not belong to an employer organization.
   */
  private UUID resolveOrganizationId(Role role, String organizationName, String normalizedEmail) {
    if (role != Role.RECRUITER) {
      if (organizationName != null && !organizationName.isBlank()) {
        throw new InvalidRegistrationException(
            "An organization name is only accepted for recruiter registration.");
      }
      return null;
    }

    String name = organizationName == null ? "" : organizationName.trim().replaceAll("\\s+", " ");
    if (name.length() < 2) {
      AuditService.auth(
          AuditService.AuthEvent.REGISTER,
          null,
          normalizedEmail,
          AuditService.Outcome.FAILURE,
          "Missing organization name");
      throw new InvalidRegistrationException(
          "An organization name is required for recruiter registration.");
    }
    if (name.length() > 255) {
      throw new InvalidRegistrationException(
          "The organization name must be 255 characters or fewer.");
    }

    Organization organization =
        organizationRepository.saveAndFlush(
            new Organization(UUID.randomUUID(), name, Instant.now(clock)));
    return organization.getId();
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
