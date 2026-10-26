package com.smarthire.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration request.
 *
 * <p>The role selects which self-service identity is created:
 *
 * <ul>
 *   <li>{@code CANDIDATE} — the default when {@code role} is omitted, preserving the original
 *       contract for existing clients.
 *   <li>{@code RECRUITER} — requires {@code organizationName}; the account is created with a new
 *       organization so job ownership works immediately.
 *   <li>{@code ADMIN} — requires a deployment-specific {@code adminInviteCode}; open administrator
 *       signup is never permitted.
 * </ul>
 *
 * <p>Password policy is a minimum length at the trust boundary (governance §16); it is never logged
 * and never stored in plaintext. The invitation code is likewise never logged or echoed.
 */
@Schema(name = "RegisterRequest", description = "Request payload for role-aware user registration")
public class RegisterRequest {

  @NotBlank
  @Email
  @Size(max = 255)
  @Schema(
      name = "email",
      description = "User's email address",
      example = "user@example.com",
      requiredMode = Schema.RequiredMode.REQUIRED,
      maxLength = 255)
  private final String email;

  @NotBlank
  @Size(min = 12, max = 128)
  @Schema(
      name = "password",
      description = "User's password (min 12 chars, max 128)",
      example = "securePassword123",
      requiredMode = Schema.RequiredMode.REQUIRED,
      minLength = 12,
      maxLength = 128)
  private final String password;

  @Pattern(
      regexp = "(?i)CANDIDATE|RECRUITER|ADMIN",
      message = "role must be one of CANDIDATE, RECRUITER or ADMIN")
  @Schema(
      name = "role",
      description = "Self-service role to create; defaults to CANDIDATE when omitted",
      example = "CANDIDATE",
      allowableValues = {"CANDIDATE", "RECRUITER", "ADMIN"})
  private final String role;

  @Size(max = 255)
  @Schema(
      name = "organizationName",
      description = "Employer organization for a RECRUITER account; ignored for other roles",
      example = "Acme Corp",
      maxLength = 255)
  private final String organizationName;

  @Size(max = 256)
  @Schema(
      name = "adminInviteCode",
      description = "Deployment-issued invitation code required for an ADMIN account",
      maxLength = 256,
      accessMode = Schema.AccessMode.WRITE_ONLY)
  private final String adminInviteCode;

  @JsonCreator
  public RegisterRequest(
      @JsonProperty("email") String email,
      @JsonProperty("password") String password,
      @JsonProperty("role") String role,
      @JsonProperty("organizationName") String organizationName,
      @JsonProperty("adminInviteCode") String adminInviteCode) {
    this.email = email;
    this.password = password;
    this.role = role;
    this.organizationName = organizationName;
    this.adminInviteCode = adminInviteCode;
  }

  /** Backwards-compatible candidate registration used by earlier clients and tests. */
  public RegisterRequest(String email, String password) {
    this(email, password, null, null, null);
  }

  public String email() {
    return email;
  }

  public String password() {
    return password;
  }

  public String role() {
    return role;
  }

  public String organizationName() {
    return organizationName;
  }

  public String adminInviteCode() {
    return adminInviteCode;
  }
}
