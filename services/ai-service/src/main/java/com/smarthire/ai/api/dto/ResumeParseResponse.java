package com.smarthire.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Response containing structured fields extracted from a resume. */
@Schema(name = "ResumeParseResponse", description = "AI-parsed resume fields (advisory only)")
public record ResumeParseResponse(
    @Schema(
            name = "parseId",
            description = "Unique identifier for this parse",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID parseId,
    @Schema(
            name = "documentId",
            description = "Document UUID",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
        UUID documentId,
    @Schema(name = "skills", description = "Extracted technical and soft skills", required = true)
        List<String> skills,
    @Schema(name = "experience", description = "Extracted work experience entries", required = true)
        List<String> experience,
    @Schema(name = "education", description = "Extracted education entries", required = true)
        List<String> education,
    @Schema(name = "certifications", description = "Extracted certifications", required = true)
        List<String> certifications,
    @Schema(
            name = "model",
            description = "AI model used",
            example = "claude-3-haiku-20240307",
            required = true)
        String model,
    @Schema(
            name = "promptVersion",
            description = "Prompt version used",
            example = "v1.0",
            required = true)
        String promptVersion) {

  public ResumeParseResponse {
    skills = skills != null ? List.copyOf(skills) : List.of();
    experience = experience != null ? List.copyOf(experience) : List.of();
    education = education != null ? List.copyOf(education) : List.of();
    certifications = certifications != null ? List.copyOf(certifications) : List.of();
  }
}
