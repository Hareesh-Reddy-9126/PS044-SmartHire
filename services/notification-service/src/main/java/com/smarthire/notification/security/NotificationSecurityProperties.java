package com.smarthire.notification.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT security properties for notification-service (mirrors job-service pattern). */
@ConfigurationProperties(prefix = "smarthire.notification.jwt")
@Validated
public record NotificationSecurityProperties(String jwkSetUri, String issuer, String audience) {}
