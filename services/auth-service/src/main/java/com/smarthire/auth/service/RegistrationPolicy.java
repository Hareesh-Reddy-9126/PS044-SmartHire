package com.smarthire.auth.service;

import com.smarthire.auth.config.RegistrationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Decides whether a requested self-service registration is allowed.
 *
 * <p>Candidates and recruiters register openly. Administrator accounts are privileged platform
 * operations accounts, so they require a deployment-specific invitation code. The comparison runs
 * over SHA-256 digests with {@link MessageDigest#isEqual(byte[], byte[])} so neither the code value
 * nor its length leaks through timing. When no code is configured, administrator registration is
 * closed rather than silently accepting an empty value.
 */
@Component
public class RegistrationPolicy {

  private final byte[] adminInviteDigest;

  public RegistrationPolicy(RegistrationProperties properties) {
    this.adminInviteDigest = digest(properties.adminInviteCode());
  }

  /** True only when the deployment has configured an administrator invitation code. */
  public boolean adminRegistrationEnabled() {
    return adminInviteDigest.length > 0;
  }

  /**
   * Constant-time check of a supplied administrator invitation code.
   *
   * @param suppliedCode value from the registration request; may be {@code null}
   * @return true when administrator registration is enabled and the code matches
   */
  public boolean isAdminInviteCodeValid(String suppliedCode) {
    if (!adminRegistrationEnabled() || suppliedCode == null || suppliedCode.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(adminInviteDigest, digest(suppliedCode.trim()));
  }

  private static byte[] digest(String value) {
    if (value == null || value.isBlank()) {
      return new byte[0];
    }
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      // SHA-256 is required by the Java platform; this cannot happen on a supported JVM.
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
