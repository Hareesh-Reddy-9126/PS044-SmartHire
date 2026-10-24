package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request to parse a resume document. */
@Schema(
    name = "ResumeParseRequest",
    description = "Request to parse a resume document into structured fields")
public record ResumeParseRequest(
    @Schema(
            name = "documentId",
            description = "Document UUID (must be a RESUME type document)",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        @NotNull
        UUID documentId) {}
