package com.smarthire.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration request. Password policy is a minimum length at the trust boundary
 * (governance §16); it is never logged and never stored in plaintext.
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 12, max = 128) String password) {}
