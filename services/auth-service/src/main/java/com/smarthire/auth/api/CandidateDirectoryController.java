package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.CandidateEmailBatchRequest;
import com.smarthire.auth.api.dto.CandidateEmailResponse;
import com.smarthire.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Candidate directory endpoint for recruiter and administrator screens.
 *
 * <p>Applications expose a candidate id but not an email address, and candidate names are not part
 * of the identity model. This endpoint resolves the one human-readable identity the product holds —
 * the candidate's email — so recruiters do not have to work with opaque UUIDs.
 *
 * <p>Access is limited to recruiters and administrators, and the service only returns accounts
 * whose role is {@code CANDIDATE}. That keeps the endpoint from becoming a general user directory:
 * asking for an administrator or recruiter id yields no entry.
 */
@RestController
@RequestMapping("/api/v1/users/candidates")
@Tag(name = "Candidate Directory", description = "Candidate email lookups for recruiter screens")
@PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
public class CandidateDirectoryController {

  private final AuthService authService;

  public CandidateDirectoryController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(
      summary = "Resolve candidate email addresses",
      description =
          "Returns the email address for each requested candidate id. Unknown ids and accounts "
              + "that are not candidates are omitted. Recruiter and administrator only.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Resolved candidate email addresses",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CandidateEmailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Recruiter or administrator role required")
      })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/emails")
  public List<CandidateEmailResponse> emails(
      @Valid @RequestBody CandidateEmailBatchRequest request) {
    Map<UUID, String> resolved = authService.candidateEmails(request.candidateIds());
    return request.candidateIds().stream()
        .distinct()
        .filter(resolved::containsKey)
        .map(candidateId -> new CandidateEmailResponse(candidateId, resolved.get(candidateId)))
        .toList();
  }
}
