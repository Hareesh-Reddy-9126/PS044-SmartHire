package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Batch request for candidate email addresses.
 *
 * <p>Recruiter and administrator screens use this to show an applicant's email instead of an opaque
 * candidate id. The batch is capped so one request cannot become a directory dump.
 */
@Schema(
    name = "CandidateEmailBatchRequest",
    description = "Candidate ids whose email addresses should be resolved")
public record CandidateEmailBatchRequest(
    @Schema(
            description = "Candidate user ids (maximum 100 per request)",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
        @NotEmpty
        @Size(max = 100)
        List<@NotNull UUID> candidateIds) {}
