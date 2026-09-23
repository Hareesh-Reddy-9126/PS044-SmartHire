package com.smarthire.application.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT security properties for application-service (mirrors job-service pattern). */
@ConfigurationProperties(prefix = "smarthire.application.jwt")
@Validated
public record ApplicationSecurityProperties(String jwkSetUri, String issuer, String audience) {}
