package com.smarthire.job.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to update an existing job's fields. Status changes are handled by the dedicated lifecycle
 * endpoints ({@code /open}, {@code /close}, {@code /reopen}).
 */
public record JobUpdateRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Size(max = 255) String role,
    @NotBlank @Size(max = 255) String location) {}
