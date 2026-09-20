package com.smarthire.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login request. */
public record LoginRequest(
    @NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 128) String password) {}
