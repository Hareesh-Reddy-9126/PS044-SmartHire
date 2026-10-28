package com.smarthire.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One resolved candidate email address.
 *
 * <p>Only candidate accounts are returned. Unknown ids, recruiters and administrators are omitted
 * so the endpoint cannot be used to enumerate non-candidate accounts.
 */
@Schema(name = "CandidateEmailResponse", description = "Email address for one candidate user id")
public record CandidateEmailResponse(
    @Schema(description = "Candidate user id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID candidateId,
    @Schema(description = "Candidate email address", example = "candidate@example.com")
        String email) {}
