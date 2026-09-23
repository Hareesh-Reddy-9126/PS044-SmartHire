package com.smarthire.recruitment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to create a new recruitment record. */
public record RecruitmentCreateRequest(@NotNull UUID applicationId) {}
