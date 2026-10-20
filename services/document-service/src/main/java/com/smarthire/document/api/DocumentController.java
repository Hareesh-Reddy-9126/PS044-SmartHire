package com.smarthire.document.api;

import com.smarthire.document.api.dto.DocumentResponse;
import com.smarthire.document.api.dto.DocumentUploadRequest;
import com.smarthire.document.domain.DocumentType;
import com.smarthire.document.security.DocumentPrincipal;
import com.smarthire.document.service.DocumentForbiddenException;
import com.smarthire.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document management endpoints. Versioned base path {@code /api/v1} from day one (governance §11).
 *
 * <p>Every method derives the caller's identity from {@code @AuthenticationPrincipal Jwt} — the
 * token the resource-server filter already verified. The request body, query string and headers are
 * never consulted for ownership, so a caller cannot claim an identity they do not hold.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document Management", description = "Endpoints for managing candidate documents")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  /**
   * Uploads a new document for the authenticated candidate.
   *
   * <p>Candidate must be authenticated. The {@code candidateId} comes from the verified JWT {@code
   * sub} claim — never from the request body. The file is uploaded as multipart/form-data with a
   * JSON {@code metadata} part; the size and content-type rules configured under {@code
   * smarthire.document.upload} are applied by the service layer.
   *
   * @param request the upload metadata (document type)
   * @param file the file to upload
   * @param jwt the verified JWT
   * @return the created document metadata
   */
  @Operation(
      summary = "Upload a document",
      description =
          "Uploads a new document for the authenticated candidate. Requires CANDIDATE role. File is uploaded as multipart/form-data with metadata part.",
      parameters = {
        @Parameter(
            name = "metadata",
            description = "Document metadata",
            required = true,
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentUploadRequest.class))),
        @Parameter(
            name = "file",
            description = "File to upload",
            required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
      })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Document uploaded successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed or file too large",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<DocumentResponse> uploadDocument(
      @Valid @RequestPart("metadata") DocumentUploadRequest request,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal Jwt jwt)
      throws IOException {

    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    if (principal.candidateId() == null) {
      // The token is valid but its subject is not a candidate UUID, so there is nobody to own the
      // document. Refusing here keeps an unowned row out of the aggregate.
      throw new DocumentForbiddenException(
          "Your access token does not identify a candidate, so it cannot own a document.");
    }

    var document =
        documentService.upload(
            principal.candidateId(),
            request.documentType(),
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            file.getInputStream());

    return ResponseEntity.created(URI.create("/api/v1/documents/" + document.getDocumentId()))
        .body(DocumentResponse.from(document));
  }

  /**
   * Retrieves a document's metadata by ID.
   *
   * <p>Candidate can view their own document metadata. Recruiter can view documents for their
   * organization's candidates (if linked via recruitment). ADMIN can view any document. Others
   * receive 404 to prevent probing.
   */
  @Operation(
      summary = "Get document metadata by ID",
      description =
          "Returns document metadata. Candidate can view their own, recruiters can view their organization's candidates' documents, admins can view any. Others receive 404.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document metadata retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/{documentId}")
  public ResponseEntity<DocumentResponse> getDocument(
      @PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    var document =
        documentService.get(
            documentId, principal.candidateId(), principal.orgId(), principal.isAdmin());
    return ResponseEntity.ok(DocumentResponse.from(document));
  }

  /**
   * Retrieves the file content for a document.
   *
   * <p>One authorized lookup supplies both the bytes and the metadata needed to describe them.
   */
  @Operation(
      summary = "Download document content",
      description =
          "Downloads the file content of a document. Same authorization rules as metadata retrieval.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "File content returned",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/{documentId}/content")
  public ResponseEntity<InputStreamResource> getDocumentContent(
      @PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) throws IOException {
    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    DocumentService.DocumentContent content =
        documentService.getContent(
            documentId, principal.candidateId(), principal.orgId(), principal.isAdmin());

    return ResponseEntity.ok()
        .contentType(safeMediaType(content.document().getContentType()))
        .contentLength(content.document().getFileSize())
        .header(
            HttpHeaders.CONTENT_DISPOSITION, contentDisposition(content.document().getFileName()))
        .body(new InputStreamResource(content.content()));
  }

  /** Lists all documents belonging to the authenticated candidate. */
  @Operation(
      summary = "List candidate documents",
      description =
          "Returns a paginated list of all documents belonging to the authenticated candidate. Requires CANDIDATE role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Documents retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = Page.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<Page<DocumentResponse>> listDocuments(
      @PageableDefault(size = 20) Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    var page = documentService.listForCandidate(principal.candidateId(), pageable);
    return ResponseEntity.ok(page.map(DocumentResponse::from));
  }

  /** Lists documents of a specific type for the authenticated candidate. */
  @Operation(
      summary = "List candidate documents by type",
      description =
          "Returns all documents of a specific type for the authenticated candidate. Requires CANDIDATE role.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Documents retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = List.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid document type",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @GetMapping("/type/{documentType}")
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<List<DocumentResponse>> listDocumentsByType(
      @Parameter(description = "Document type", example = "RESUME") @PathVariable
          String documentType,
      @AuthenticationPrincipal Jwt jwt) {
    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    DocumentType type = DocumentType.parse(documentType);
    var documents = documentService.listForCandidateByType(principal.candidateId(), type);
    return ResponseEntity.ok(documents.stream().map(DocumentResponse::from).toList());
  }

  /**
   * Deletes a candidate's document.
   *
   * <p>Only the owning candidate may delete their document.
   */
  @Operation(
      summary = "Delete a document",
      description =
          "Deletes a document owned by the authenticated candidate. Requires CANDIDATE role.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - requires CANDIDATE role and ownership",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
      })
  @DeleteMapping("/{documentId}")
  @PreAuthorize("hasRole('CANDIDATE')")
  public ResponseEntity<Void> deleteDocument(
      @PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    DocumentPrincipal principal = DocumentPrincipal.from(jwt);
    documentService.delete(documentId, principal.candidateId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Falls back to {@code application/octet-stream} when a stored content type is not a parseable
   * media type, so a legacy or hand-edited row cannot turn a download into a 500.
   */
  private MediaType safeMediaType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (InvalidMediaTypeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  /**
   * Builds the {@code Content-Disposition} value through Spring's own builder, which quotes the
   * filename for us. Hand-concatenating it would let a name containing a quote or a control
   * character alter the header.
   */
  private String contentDisposition(String fileName) {
    return ContentDisposition.inline()
        .filename(fileName == null || fileName.isBlank() ? "document" : fileName)
        .build()
        .toString();
  }
}
