package com.smarthire.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Identity Services settings.
 *
 * <p>{@code clientId} is the public OAuth 2.0 Web application client id. It is not a secret: the
 * browser needs the same value to request an ID token, and the server checks that token's audience
 * against it. A blank value disables Google sign-in without affecting email/password login.
 */
@ConfigurationProperties("smarthire.google")
public record GoogleProperties(String clientId) {

  public boolean isConfigured() {
    return clientId != null && !clientId.isBlank() && !clientId.contains("${");
  }
}
