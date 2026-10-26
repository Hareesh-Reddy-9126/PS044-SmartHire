package com.smarthire.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public-registration controls for the self-service roles.
 *
 * <p>The administrator invitation code is a secret and is therefore supplied only through the
 * environment ({@code AUTH_ADMIN_SIGNUP_CODE}). A blank value disables administrator
 * self-registration entirely; no default or demo code is ever committed (decision 8).
 */
@ConfigurationProperties("smarthire.registration")
public record RegistrationProperties(String adminInviteCode) {

  public RegistrationProperties {
    adminInviteCode = adminInviteCode == null ? "" : adminInviteCode.trim();
  }
}
