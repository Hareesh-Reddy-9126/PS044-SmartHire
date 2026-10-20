package com.smarthire.document.api.dto;

import com.smarthire.document.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request DTO for document upload metadata (sent as part of multipart form data). */
@Schema(name = "DocumentUploadRequest", description = "Metadata for document upload")
public record DocumentUploadRequest(
    @Schema(
            name = "documentType",
            description = "Type of document",
            example = "RESUME",
            required = true)
        @NotNull
        DocumentType documentType) {}
