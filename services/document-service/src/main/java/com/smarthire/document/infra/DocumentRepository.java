package com.smarthire.document.infra;

import com.smarthire.document.domain.Document;
import com.smarthire.document.domain.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence port for {@link Document} (Spring Data JPA). Lives in {@code infra} per governance
 * §3.
 */
public interface DocumentRepository
    extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

  Optional<Document> findByDocumentId(UUID documentId);

  Page<Document> findByCandidateId(UUID candidateId, Pageable pageable);

  List<Document> findByCandidateId(UUID candidateId);

  List<Document> findByCandidateIdAndDocumentType(UUID candidateId, DocumentType documentType);

  boolean existsByDocumentId(UUID documentId);
}
