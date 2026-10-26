package com.smarthire.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development seed credentials for demo RECRUITER and ADMIN accounts. Both roles can also
 * self-register. Values come from the environment and are blank by default: no known/reusable demo
 * password is ever committed (decision 8). A blank email or password means that account is not
 * seeded.
 */
@ConfigurationProperties("smarthire.seed")
public record SeedProperties(
    String recruiterEmail, String recruiterPassword, String adminEmail, String adminPassword) {}
