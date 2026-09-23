package com.smarthire.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request to create a new application. The candidate is derived from the authenticated JWT. */
public record ApplicationCreateRequest(
    @NotNull UUID jobId, @NotBlank @Size(max = 2048) String resumeLink) {}
