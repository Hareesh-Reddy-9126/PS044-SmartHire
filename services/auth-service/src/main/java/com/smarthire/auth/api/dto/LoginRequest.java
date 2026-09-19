package com.smarthire.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Login request. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
