package com.smarthire.recruitment.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT security properties for recruitment-service (mirrors job-service pattern). */
@ConfigurationProperties(prefix = "smarthire.recruitment.jwt")
@Validated
public record RecruitmentSecurityProperties(String jwkSetUri, String issuer, String audience) {}
