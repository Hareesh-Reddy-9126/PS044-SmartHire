package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.FinalStatus;
import jakarta.validation.constraints.NotNull;

/** Request to update final status. */
public record FinalStatusUpdateRequest(@NotNull FinalStatus status) {}
