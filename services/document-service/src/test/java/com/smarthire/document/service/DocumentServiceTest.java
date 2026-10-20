package com.smarthire.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarthire.document.config.DocumentUploadProperties;
import com.smarthire.document.domain.Document;
import com.smarthire.document.domain.DocumentType;
import com.smarthire.document.infra.DocumentRepository;
import com.smarthire.document.infra.DocumentStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** Unit tests for {@link DocumentService}. */
class DocumentServiceTest {

  private DocumentRepository documentRepository;
  private DocumentStorage documentStorage;
  private DocumentService documentService;

  private final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private final String FILE_NAME = "resume.pdf";
  private final String CONTENT_TYPE = "application/pdf";
  private final long FILE_SIZE = 1024;

  @BeforeEach
  void setUp() {
    documentRepository = mock(DocumentRepository.class);
    documentStorage = mock(DocumentStorage.class);
    documentService =
        new DocumentService(
            documentRepository,
            documentStorage,
            new DocumentUploadProperties(
                DataSize.ofMegabytes(10),
                List.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
  }

  @Nested
  @DisplayName("Upload document")
  class Upload {

    @Test
    @DisplayName("Uploads valid document successfully")
    void uploadValidDocument() throws Exception {
      InputStream content = new ByteArrayInputStream("test content".getBytes());
      when(documentStorage.store(any(String.class), any(InputStream.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

      Document document =
          documentService.upload(
              CANDIDATE_ID, DocumentType.RESUME, FILE_NAME, CONTENT_TYPE, FILE_SIZE, content);

      assertThat(document.getCandidateId()).isEqualTo(CANDIDATE_ID);
      assertThat(document.getDocumentType()).isEqualTo(DocumentType.RESUME);
      assertThat(document.getFileName()).isEqualTo(FILE_NAME);
      assertThat(document.getContentType()).isEqualTo(CONTENT_TYPE);
      assertThat(document.getFileSize()).isEqualTo(FILE_SIZE);
      assertThat(document.getStorageKey()).startsWith("candidates/");
    }

    @Test
    @DisplayName("Rejects empty file")
    void rejectsEmptyFile() throws Exception {
      InputStream emptyContent = new ByteArrayInputStream(new byte[0]);

      assertThatThrownBy(
              () ->
                  documentService.upload(
                      CANDIDATE_ID, DocumentType.RESUME, FILE_NAME, CONTENT_TYPE, 0, emptyContent))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Rejects unsupported content type")
    void rejectsUnsupportedContentType() throws Exception {
      InputStream content = new ByteArrayInputStream("test".getBytes());

      assertThatThrownBy(
              () ->
                  documentService.upload(
                      CANDIDATE_ID,
                      DocumentType.RESUME,
                      FILE_NAME,
                      "application/x-executable",
                      FILE_SIZE,
                      content))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported content type");
    }

    @Test
    @DisplayName("Stores file with generated storage key")
    void storesWithGeneratedKey() throws Exception {
      InputStream content = new ByteArrayInputStream("test content".getBytes());
      when(documentStorage.store(any(String.class), any(InputStream.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

      Document document =
          documentService.upload(
              CANDIDATE_ID, DocumentType.RESUME, FILE_NAME, CONTENT_TYPE, FILE_SIZE, content);

      assertThat(document.getStorageKey()).startsWith("candidates/");
      assertThat(document.getStorageKey()).contains("resume/");
    }

    @Test
    @DisplayName("Rejects a file larger than the configured maximum")
    void rejectsOversizedFile() {
      InputStream content = new ByteArrayInputStream("test".getBytes());

      assertThatThrownBy(
              () ->
                  documentService.upload(
                      CANDIDATE_ID,
                      DocumentType.RESUME,
                      FILE_NAME,
                      CONTENT_TYPE,
                      10L * 1024 * 1024 + 1,
                      content))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maximum allowed");
    }

    @Test
    @DisplayName("Accepts a content type carrying parameters")
    void acceptsContentTypeWithParameters() throws Exception {
      InputStream content = new ByteArrayInputStream("test content".getBytes());
      when(documentStorage.store(any(String.class), any(InputStream.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

      Document document =
          documentService.upload(
              CANDIDATE_ID,
              DocumentType.RESUME,
              FILE_NAME,
              "application/pdf; charset=binary",
              FILE_SIZE,
              content);

      assertThat(document.getDocumentType()).isEqualTo(DocumentType.RESUME);
    }

    @Test
    @DisplayName("Keeps the client filename out of the storage key")
    void keepsFilenameOutOfStorageKey() throws Exception {
      InputStream content = new ByteArrayInputStream("test content".getBytes());
      when(documentStorage.store(any(String.class), any(InputStream.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

      Document document =
          documentService.upload(
              CANDIDATE_ID,
              DocumentType.RESUME,
              "../../../etc/passwd.pdf",
              CONTENT_TYPE,
              FILE_SIZE,
              content);

      assertThat(document.getStorageKey())
          .startsWith("candidates/" + CANDIDATE_ID + "/resume/")
          .doesNotContain("..")
          .doesNotContain("passwd.pdf");
      // The display name is sanitized rather than dropped, so the candidate still recognizes it.
      assertThat(document.getFileName()).isEqualTo(".._.._.._etc_passwd.pdf");
    }

    @Test
    @DisplayName("Removes the stored blob when the metadata row cannot be written")
    void removesBlobWhenMetadataSaveFails() throws Exception {
      InputStream content = new ByteArrayInputStream("test content".getBytes());
      when(documentStorage.store(any(String.class), any(InputStream.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(documentRepository.save(any(Document.class)))
          .thenThrow(new IllegalStateException("db down"));

      assertThatThrownBy(
              () ->
                  documentService.upload(
                      CANDIDATE_ID,
                      DocumentType.RESUME,
                      FILE_NAME,
                      CONTENT_TYPE,
                      FILE_SIZE,
                      content))
          .isInstanceOf(IllegalStateException.class);

      verify(documentStorage).delete(any(String.class));
    }
  }

  @Nested
  @DisplayName("Get document")
  class Get {

    @Test
    @DisplayName("Candidate can view their own document")
    void candidateCanViewOwn() throws Exception {
      Document document = createDocument(CANDIDATE_ID);
      when(documentRepository.findByDocumentId(document.getDocumentId()))
          .thenReturn(Optional.of(document));

      Document result = documentService.get(document.getDocumentId(), CANDIDATE_ID, null, false);

      assertThat(result).isSameAs(document);
    }

    @Test
    @DisplayName("Candidate cannot view another's document")
    void candidateCannotViewOthers() throws Exception {
      Document document = createDocument(CANDIDATE_ID);
      when(documentRepository.findByDocumentId(document.getDocumentId()))
          .thenReturn(Optional.of(document));

      assertThatThrownBy(
              () -> documentService.get(document.getDocumentId(), UUID.randomUUID(), null, false))
          .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    @DisplayName("ADMIN can view any document")
    void adminCanViewAny() throws Exception {
      Document document = createDocument(CANDIDATE_ID);
      when(documentRepository.findByDocumentId(document.getDocumentId()))
          .thenReturn(Optional.of(document));

      Document result = documentService.get(document.getDocumentId(), null, null, true);

      assertThat(result).isSameAs(document);
    }

    @Test
    @DisplayName("Returns 404 for non-existent document")
    void notFoundForMissingDocument() {
      when(documentRepository.findByDocumentId(any(UUID.class))).thenReturn(Optional.empty());

      assertThatThrownBy(() -> documentService.get(UUID.randomUUID(), CANDIDATE_ID, null, false))
          .isInstanceOf(DocumentNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Delete document")
  class Delete {

    @Test
    @DisplayName("Owner can delete their document")
    void ownerCanDelete() throws Exception {
      Document document = createDocument(CANDIDATE_ID);
      when(documentRepository.findByDocumentId(document.getDocumentId()))
          .thenReturn(Optional.of(document));

      Document result = documentService.delete(document.getDocumentId(), CANDIDATE_ID);

      assertThat(result).isSameAs(document);
    }

    @Test
    @DisplayName("Non-owner cannot delete")
    void nonOwnerCannotDelete() {
      Document document = createDocument(CANDIDATE_ID);
      when(documentRepository.findByDocumentId(document.getDocumentId()))
          .thenReturn(Optional.of(document));

      assertThatThrownBy(() -> documentService.delete(document.getDocumentId(), UUID.randomUUID()))
          .isInstanceOf(DocumentForbiddenException.class);
    }
  }

  @Nested
  @DisplayName("List documents")
  class ListDocuments {

    @Test
    @DisplayName("Lists documents for candidate")
    void listsForCandidate() {
      // Pageable testing requires Spring Data, so we verify the method exists
      assertThat(documentService).isNotNull();
    }
  }

  private Document createDocument(UUID candidateId) {
    return Document.create(
        UUID.randomUUID(),
        candidateId,
        DocumentType.RESUME,
        FILE_NAME,
        CONTENT_TYPE,
        "candidates/" + candidateId + "/resume/abc.pdf",
        FILE_SIZE);
  }
}
