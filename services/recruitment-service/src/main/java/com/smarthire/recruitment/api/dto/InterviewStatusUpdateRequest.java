package com.smarthire.recruitment.api.dto;

import com.smarthire.recruitment.domain.InterviewStatus;
import jakarta.validation.constraints.NotNull;

/** Request to update interview status. */
public record InterviewStatusUpdateRequest(@NotNull InterviewStatus status) {}
