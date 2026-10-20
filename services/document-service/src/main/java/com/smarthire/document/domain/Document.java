package com.smarthire.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Document aggregate. Fields are the Project-44 source fields — {@code document_id}, {@code
 * candidate_id}, {@code document_type}, {@code file_name}, {@code content_type}, {@code
 * storage_key}, {@code file_size} — persisted to {@code document_svc.documents}.
 *
 * <p>The aggregate stores metadata about uploaded documents. The actual file content is stored in a
 * separate storage backend (local filesystem for R1, configurable for cloud storage later).
 */
@Entity
@Table(name = "documents")
public class Document {

  @Id
  @Column(name = "document_id", nullable = false, updatable = false)
  private UUID documentId;

  @Column(name = "candidate_id", nullable = false, updatable = false)
  private UUID candidateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA. */
  protected Document() {}

  /**
   * Creates a document with explicit state. Every document has a candidate from the moment it
   * exists — there is no ownerless constructor, so an unowned row cannot be built in Java.
   */
  public Document(
      UUID documentId,
      UUID candidateId,
      DocumentType documentType,
      String fileName,
      String contentType,
      String storageKey,
      long fileSize,
      Instant createdAt,
      Instant updatedAt) {
    this.documentId = documentId;
    this.candidateId = candidateId;
    this.documentType = documentType;
    this.fileName = fileName;
    this.contentType = contentType;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Creates a new document for a candidate. */
  public static Document create(
      UUID documentId,
      UUID candidateId,
      DocumentType documentType,
      String fileName,
      String contentType,
      String storageKey,
      long fileSize) {
    Instant now = Instant.now();
    return new Document(
        documentId,
        candidateId,
        documentType,
        fileName,
        contentType,
        storageKey,
        fileSize,
        now,
        now);
  }

  /** True when the given candidate owns this document. A {@code null} caller never owns. */
  public boolean isOwnedBy(UUID callerCandidateId) {
    return callerCandidateId != null && callerCandidateId.equals(candidateId);
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getCandidateId() {
    return candidateId;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public long getFileSize() {
    return fileSize;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
