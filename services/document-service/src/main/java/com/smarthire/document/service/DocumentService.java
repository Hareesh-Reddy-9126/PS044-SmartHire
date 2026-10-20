package com.smarthire.document.service;

import com.smarthire.common.audit.AuditService;
import com.smarthire.document.config.DocumentUploadProperties;
import com.smarthire.document.domain.Document;
import com.smarthire.document.domain.DocumentType;
import com.smarthire.document.infra.DocumentRepository;
import com.smarthire.document.infra.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document service for the Document aggregate.
 *
 * <p>This is the object-level authorization layer. The filter chain has already established
 * <em>that</em> the caller is authenticated and method security has established <em>which role</em>
 * they hold; here we decide whether this particular caller may see or change this particular
 * document. {@code callerCandidateId} always originates from the verified JWT {@code sub} claim for
 * candidates; {@code callerOrgId} from the JWT {@code orgId} claim for recruiters — never from a
 * body, query parameter or header.
 *
 * <p>Visibility rules:
 *
 * <ul>
 *   <li>Candidate: can see only their own documents (candidate_id match).
 *   <li>Recruiter: can see documents for applications they own (via recruitment ownership).
 *   <li>ADMIN: platform operations — access to any resource.
 * </ul>
 */
@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final DocumentStorage documentStorage;
  private final DocumentUploadProperties uploadProperties;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentStorage documentStorage,
      DocumentUploadProperties uploadProperties) {
    this.documentRepository = documentRepository;
    this.documentStorage = documentStorage;
    this.uploadProperties = uploadProperties;
  }

  /**
   * Uploads a new document for the authenticated candidate.
   *
   * <p>The constraints configured under {@code smarthire.document.upload} are enforced here, not in
   * the controller, so no entry point can store a file the service would later refuse to serve.
   *
   * @param candidateId the candidate UUID from the verified JWT (never from request body)
   * @param documentType the type of document
   * @param originalFileName the original filename (for metadata only)
   * @param contentType the MIME content type
   * @param fileSize the file size in bytes
   * @param content the file content input stream
   * @return the created document
   * @throws IllegalArgumentException if the file is empty, too large, or of an unsupported type
   */
  @Transactional
  public Document upload(
      UUID candidateId,
      DocumentType documentType,
      String originalFileName,
      String contentType,
      long fileSize,
      InputStream content)
      throws IOException {

    validateUpload(candidateId, documentType, contentType, fileSize);

    // Generate a server-side storage key (not using the original filename)
    String storageKey = generateStorageKey(candidateId, documentType, originalFileName);

    // Store the file
    try {
      documentStorage.store(storageKey, content);
    } catch (DocumentStorage.StorageException e) {
      throw new IOException("Failed to store document", e);
    }

    // Create document metadata. If the row cannot be written, the file it points at is removed
    // again: a stored blob with no metadata row is unreachable through the API and would otherwise
    // occupy storage for the lifetime of the deployment.
    Document document =
        Document.create(
            UUID.randomUUID(),
            candidateId,
            documentType,
            sanitizeFileName(originalFileName),
            contentType,
            storageKey,
            fileSize);

    try {
      Document saved = documentRepository.save(document);
      AuditService.document(
          AuditService.DocumentEvent.UPLOAD,
          candidateId,
          saved.getDocumentId(),
          AuditService.Outcome.SUCCESS,
          "documentType="
              + documentType.name()
              + ",fileName="
              + sanitizeFileName(originalFileName)
              + ",fileSize="
              + fileSize);
      return saved;
    } catch (RuntimeException e) {
      discardQuietly(storageKey);
      throw e;
    }
  }

  /**
   * Retrieves a document by ID, subject to visibility rules.
   *
   * @param documentId the document to read
   * @param callerCandidateId the candidate UUID from JWT (null if not a candidate)
   * @param callerOrgId the org UUID from JWT (null if not a recruiter)
   * @param isAdmin whether the caller has ADMIN role
   * @return the document
   * @throws DocumentNotFoundException if not found or not visible
   * @throws DocumentForbiddenException if caller lacks access
   */
  @Transactional(readOnly = true)
  public Document get(UUID documentId, UUID callerCandidateId, UUID callerOrgId, boolean isAdmin) {
    Document document = load(documentId);

    if (isAdmin) {
      return document;
    }

    // Candidate can only see their own
    if (callerCandidateId != null && document.isOwnedBy(callerCandidateId)) {
      return document;
    }

    // Recruiter access: only if they own the application/recruitment this document belongs to
    // For R1 MVP, recruiters cannot directly access documents
    // Future: add recruiter access via recruitment ownership

    throw new DocumentNotFoundException("Document not found: " + documentId);
  }

  /** Lists documents for a candidate. */
  @Transactional(readOnly = true)
  public Page<Document> listForCandidate(UUID candidateId, Pageable pageable) {
    return documentRepository.findByCandidateId(candidateId, pageable);
  }

  /** Lists documents for a candidate by type. */
  @Transactional(readOnly = true)
  public List<Document> listForCandidateByType(UUID candidateId, DocumentType documentType) {
    return documentRepository.findByCandidateIdAndDocumentType(candidateId, documentType);
  }

  /**
   * Retrieves the file content for a document, together with the metadata needed to serve it, so
   * the caller performs a single authorized load.
   *
   * @param documentId the document ID
   * @param callerCandidateId the candidate UUID from JWT
   * @param callerOrgId the org UUID from JWT
   * @param isAdmin whether the caller has ADMIN role
   * @return the document metadata paired with an open stream of its content
   * @throws DocumentNotFoundException if not found or not visible
   * @throws IOException if retrieval fails
   */
  public DocumentContent getContent(
      UUID documentId, UUID callerCandidateId, UUID callerOrgId, boolean isAdmin)
      throws IOException {
    Document document = get(documentId, callerCandidateId, callerOrgId, isAdmin);
    try {
      return new DocumentContent(document, documentStorage.retrieve(document.getStorageKey()));
    } catch (DocumentStorage.StorageException e) {
      throw new IOException("Failed to retrieve document content", e);
    }
  }

  /**
   * Deletes a candidate's document.
   *
   * @param documentId the document to delete
   * @param callerCandidateId the candidate UUID from JWT
   * @return the deleted document
   * @throws DocumentNotFoundException if not found
   * @throws DocumentForbiddenException if not the owner
   */
  @Transactional
  public Document delete(UUID documentId, UUID callerCandidateId) {
    Document document = loadOwned(documentId, callerCandidateId);

    // The blob is removed first: a caller who is told "deleted" must not be able to fetch the
    // content again. A blob that cannot be removed leaves an orphan rather than a live file the
    // API still serves, and the caller is not charged for a storage-level failure.
    discardQuietly(document.getStorageKey());

    documentRepository.delete(document);
    AuditService.document(
        AuditService.DocumentEvent.DELETE,
        callerCandidateId,
        documentId,
        AuditService.Outcome.SUCCESS,
        "documentType="
            + document.getDocumentType().name()
            + ",fileName="
            + document.getFileName());
    return document;
  }

  // --- helpers -------------------------------------------------------------------

  private Document load(UUID documentId) {
    return documentRepository
        .findByDocumentId(documentId)
        .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
  }

  private Document loadOwned(UUID documentId, UUID callerCandidateId) {
    Document document = load(documentId);
    if (!document.isOwnedBy(callerCandidateId)) {
      AuditService.authorizationFailure(
          "document", documentId, callerCandidateId, "Candidate does not own this document");
      throw new DocumentForbiddenException("This document belongs to another candidate.");
    }
    return document;
  }

  /**
   * Generates a server-side storage key that prevents path traversal and is unique.
   *
   * <p>The caller's filename never becomes a path segment. It contributes at most its extension,
   * and that extension is reduced to a plain suffix, so a name like {@code ../../etc/passwd.pdf}
   * cannot address anything outside the candidate's own directory.
   */
  private String generateStorageKey(
      UUID candidateId, DocumentType documentType, String originalFileName) {
    String extension = extractExtension(originalFileName);
    return String.format(
        "candidates/%s/%s/%s%s",
        candidateId, documentType.name().toLowerCase(Locale.ROOT), UUID.randomUUID(), extension);
  }

  private String extractExtension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "";
    }
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot <= 0 || lastDot == fileName.length() - 1) {
      return "";
    }
    String extension = fileName.substring(lastDot + 1);
    // Only a short alphanumeric suffix survives, so the storage key stays a flat set of segments.
    if (extension.length() > 10 || !extension.matches("[A-Za-z0-9]+")) {
      return "";
    }
    return "." + extension.toLowerCase(Locale.ROOT);
  }

  /**
   * Sanitizes the original filename for metadata storage (not for filesystem use).
   *
   * <p>Control characters are removed as well as path separators, because the name is echoed back
   * in a {@code Content-Disposition} header and a newline there would be a response-splitting
   * vector.
   */
  private String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "unnamed";
    }
    String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    sanitized = sanitized.replaceAll("[\\p{Cntrl}]", "").trim();
    if (sanitized.isEmpty()) {
      return "unnamed";
    }
    return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
  }

  // --- upload validation ----------------------------------------------------------

  /**
   * Enforces the configured upload constraints and the ownership invariants of the aggregate.
   *
   * @throws IllegalArgumentException if any constraint is violated; mapped to RFC 9457 400
   */
  private void validateUpload(
      UUID candidateId, DocumentType documentType, String contentType, long fileSize) {
    if (candidateId == null) {
      throw new IllegalArgumentException("A document must belong to a candidate.");
    }
    if (documentType == null) {
      throw new IllegalArgumentException("A document type is required.");
    }
    if (fileSize <= 0) {
      throw new IllegalArgumentException("File cannot be empty.");
    }
    long maxBytes = uploadProperties.maxFileSize().toBytes();
    if (fileSize > maxBytes) {
      throw new IllegalArgumentException(
          "File size exceeds the maximum allowed (" + uploadProperties.maxFileSize() + ").");
    }
    String baseType = baseContentType(contentType);
    if (baseType == null || !isAllowedContentType(baseType)) {
      throw new IllegalArgumentException("Unsupported content type: " + contentType);
    }
  }

  /** Strips any parameters ({@code application/pdf; charset=…}) and normalizes case. */
  private String baseContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    int parameterStart = contentType.indexOf(';');
    String base = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
    base = base.trim().toLowerCase(Locale.ROOT);
    return base.isEmpty() ? null : base;
  }

  private boolean isAllowedContentType(String baseType) {
    return uploadProperties.allowedContentTypes().stream()
        .map(this::baseContentType)
        .anyMatch(allowed -> allowed != null && allowed.equals(baseType));
  }

  /**
   * Best-effort removal of a stored file. Used when the metadata row could not be written, and on
   * delete when the blob is already gone. Failures are not propagated: the caller's real outcome
   * has already been decided, and a storage-level cleanup problem must not change it.
   */
  private void discardQuietly(String storageKey) {
    try {
      documentStorage.delete(storageKey);
    } catch (DocumentStorage.StorageException ignored) {
      // Deliberately swallowed; see the method contract.
    }
  }

  /**
   * A document's metadata paired with an open stream of its content, so serving a download needs
   * one authorized lookup instead of two.
   *
   * @param document the authorized document metadata
   * @param content an open stream the caller must close
   */
  public record DocumentContent(Document document, InputStream content) {}
}
