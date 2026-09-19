package com.smarthire.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Refresh-token exchange request. */
public record RefreshRequest(@NotBlank String refreshToken) {}
