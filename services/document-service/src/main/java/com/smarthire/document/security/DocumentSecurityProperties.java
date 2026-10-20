package com.smarthire.document.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration properties for document-service JWT verification. */
@ConfigurationProperties(prefix = "smarthire.document.jwt")
@Validated
public record DocumentSecurityProperties(
    @NotBlank String jwkSetUri, @NotBlank String issuer, @NotBlank String audience) {}
