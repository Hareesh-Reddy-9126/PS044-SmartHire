package com.smarthire.document.api.dto;

import com.smarthire.document.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Response DTO for document metadata. */
@Schema(name = "DocumentResponse", description = "Document metadata response")
public record DocumentResponse(
    @Schema(
            name = "documentId",
            description = "Unique identifier of the document",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID documentId,
    @Schema(
            name = "candidateId",
            description = "ID of the candidate who owns this document",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID candidateId,
    @Schema(name = "documentType", description = "Type of document", example = "RESUME") @NotNull
        DocumentType documentType,
    @Schema(
            name = "fileName",
            description = "Original file name",
            example = "resume.pdf",
            maxLength = 255)
        @NotNull
        @Size(max = 255)
        String fileName,
    @Schema(
            name = "contentType",
            description = "MIME type of the file",
            example = "application/pdf",
            maxLength = 100)
        @NotNull
        @Size(max = 100)
        String contentType,
    @Schema(name = "fileSize", description = "File size in bytes", example = "102400")
        long fileSize,
    @Schema(
            name = "createdAt",
            description = "Creation timestamp",
            example = "2024-01-15T10:30:00Z")
        java.time.Instant createdAt,
    @Schema(
            name = "updatedAt",
            description = "Last update timestamp",
            example = "2024-01-15T10:30:00Z")
        java.time.Instant updatedAt) {

  public static DocumentResponse from(com.smarthire.document.domain.Document document) {
    return new DocumentResponse(
        document.getDocumentId(),
        document.getCandidateId(),
        document.getDocumentType(),
        document.getFileName(),
        document.getContentType(),
        document.getFileSize(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
