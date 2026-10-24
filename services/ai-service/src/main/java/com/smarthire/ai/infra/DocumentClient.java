package com.smarthire.ai.infra;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for Document Service.
 *
 * <p>Calls {@code GET /api/v1/documents/{documentId}/content} to fetch resume content for parsing.
 * Protected by Resilience4j circuit breaker, retry, and bulkhead for fault tolerance.
 */
@FeignClient(name = "DOCUMENT-SERVICE")
public interface DocumentClient {

  /**
   * Retrieves a document's metadata by ID.
   *
   * @param documentId the document UUID
   * @return document metadata
   */
  @GetMapping("/api/v1/documents/{documentId}")
  @CircuitBreaker(name = "documentService", fallbackMethod = "getDocumentFallback")
  @Retry(name = "documentService")
  @Bulkhead(name = "documentService")
  DocumentResponse getDocument(@PathVariable("documentId") UUID documentId);

  /**
   * Retrieves the file content for a document.
   *
   * @param documentId the document UUID
   * @return document content as byte array
   */
  @GetMapping("/api/v1/documents/{documentId}/content")
  @CircuitBreaker(name = "documentService", fallbackMethod = "getDocumentContentFallback")
  @Retry(name = "documentService")
  @Bulkhead(name = "documentService")
  byte[] getDocumentContent(@PathVariable("documentId") UUID documentId);

  /** Fallback for circuit breaker / bulkhead when Document Service is unavailable. */
  default DocumentResponse getDocumentFallback(UUID documentId, Exception ex) {
    throw new DocumentServiceUnavailableException(
        "Document service unavailable: " + ex.getMessage());
  }

  /**
   * Fallback for circuit breaker / bulkhead when Document Service is unavailable on content fetch.
   */
  default byte[] getDocumentContentFallback(UUID documentId, Exception ex) {
    throw new DocumentServiceUnavailableException(
        "Document service unavailable (content): " + ex.getMessage());
  }

  /** Mirror of document-service's {@code DocumentResponse}. */
  record DocumentResponse(
      UUID documentId,
      UUID candidateId,
      String documentType,
      String fileName,
      String contentType,
      long fileSize) {}

  /** Exception thrown when Document Service is unavailable. */
  class DocumentServiceUnavailableException extends RuntimeException {
    public DocumentServiceUnavailableException(String message) {
      super(message);
    }
  }
}
