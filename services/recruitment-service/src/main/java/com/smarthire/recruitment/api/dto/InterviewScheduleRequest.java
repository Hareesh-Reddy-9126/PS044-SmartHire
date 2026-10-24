package com.smarthire.recruitment.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** Required details for scheduling an interview. */
@Schema(name = "InterviewScheduleRequest", description = "Interview schedule details")
public record InterviewScheduleRequest(
    @NotNull @FutureOrPresent LocalDate interviewDate,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime interviewStartTime,
    @NotBlank @Size(max = 200) String interviewer,
    @NotBlank @Size(max = 300) String interviewLocation) {}
