package com.smarthire.document.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.document.domain.Document;
import com.smarthire.document.domain.DocumentType;
import com.smarthire.document.security.SecurityConfig;
import com.smarthire.document.service.DocumentForbiddenException;
import com.smarthire.document.service.DocumentNotFoundException;
import com.smarthire.document.service.DocumentService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP contract for the Document API, with the production filter chain and the production exception
 * handler. Only the {@link JwtDecoder} and {@link DocumentService} are mocked, so the multipart
 * binding, the role gate, and the RFC 9457 mapping are the real ones.
 *
 * <p>The point of most of these cases is the ownership source: the service is asked for a candidate
 * id that only the verified token can supply, and the request body never gets a say.
 */
@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, DocumentExceptionHandler.class})
@TestPropertySource(
    properties = {
      "smarthire.document.jwt.jwk-set-uri=http://localhost:8082/oauth2/jwks",
      "smarthire.document.jwt.issuer=https://smarthire.local/auth",
      "smarthire.document.jwt.audience=smarthire"
    })
class DocumentControllerWebMvcTest {

  private static final UUID CANDIDATE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID OTHER_CANDIDATE_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333344");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID DOCUMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  private static final String CANDIDATE = "candidate-token";
  private static final String OTHER_CANDIDATE = "other-candidate-token";
  private static final String RECRUITER = "recruiter-token";
  private static final String ADMIN = "admin-token";

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private DocumentService documentService;

  private void givenToken(String token) {
    Jwt decoded =
        switch (token) {
          case CANDIDATE -> jwt(token, CANDIDATE_ID, null, List.of("CANDIDATE"));
          case OTHER_CANDIDATE -> jwt(token, OTHER_CANDIDATE_ID, null, List.of("CANDIDATE"));
          case RECRUITER -> jwt(token, null, ORG_ID, List.of("RECRUITER"));
          case ADMIN -> jwt(token, null, null, List.of("ADMIN"));
          default -> throw new IllegalArgumentException(token);
        };
    when(jwtDecoder.decode(token)).thenReturn(decoded);
  }

  private static Jwt jwt(String token, UUID candidateId, UUID orgId, List<String> roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject(candidateId != null ? candidateId.toString() : "not-a-uuid")
            .issuer("https://smarthire.local/auth")
            .audience(List.of("smarthire"))
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900));
    if (orgId != null) {
      builder.claim("orgId", orgId.toString());
    }
    return builder.build();
  }

  private static Document document(UUID candidateId) {
    return Document.create(
        DOCUMENT_ID,
        candidateId,
        DocumentType.RESUME,
        "resume.pdf",
        "application/pdf",
        "candidates/" + candidateId + "/resume/" + DOCUMENT_ID + ".pdf",
        4);
  }

  // ---------------------------------------------------------------------- upload

  @Nested
  class Upload {

    @Test
    void candidateUploadsWithOwnershipTakenFromTheToken() throws Exception {
      givenToken(CANDIDATE);
      Document created = document(CANDIDATE_ID);
      when(documentService.upload(
              eq(CANDIDATE_ID),
              eq(DocumentType.RESUME),
              anyString(),
              anyString(),
              anyLong(),
              any(InputStream.class)))
          .thenReturn(created);

      mvc.perform(upload(CANDIDATE, "resume.pdf", "{\"documentType\":\"RESUME\"}", "pdf bytes"))
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", "/api/v1/documents/" + DOCUMENT_ID))
          .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
          .andExpect(jsonPath("$.candidateId").value(CANDIDATE_ID.toString()));

      // The body carried no candidateId, and none was invented: the service was handed the token's.
      verify(documentService)
          .upload(
              eq(CANDIDATE_ID),
              eq(DocumentType.RESUME),
              eq("resume.pdf"),
              eq("application/pdf"),
              eq(9L),
              any(InputStream.class));
    }

    @Test
    void documentTypeIsAcceptedInAnyCase() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.upload(
              eq(CANDIDATE_ID),
              eq(DocumentType.RESUME),
              anyString(),
              anyString(),
              anyLong(),
              any(InputStream.class)))
          .thenReturn(document(CANDIDATE_ID));

      mvc.perform(upload(CANDIDATE, "resume.pdf", "{\"documentType\":\"resume\"}", "pdf bytes"))
          .andExpect(status().isCreated());
    }

    @Test
    void missingMetadataPartIsRejected() throws Exception {
      givenToken(CANDIDATE);

      MockMultipartFile file =
          new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf".getBytes());

      mvc.perform(
              multipart("/api/v1/documents")
                  .file(file)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("metadata")));
    }

    @Test
    void unknownDocumentTypeIsRejected() throws Exception {
      givenToken(CANDIDATE);

      mvc.perform(upload(CANDIDATE, "resume.pdf", "{\"documentType\":\"PASSPORT\"}", "pdf bytes"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void serviceLevelValidationFailureIsAProblemDetail() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.upload(
              any(), any(), anyString(), anyString(), anyLong(), any(InputStream.class)))
          .thenThrow(
              new IllegalArgumentException("Unsupported content type: application/x-msdownload"));

      mvc.perform(upload(CANDIDATE, "resume.exe", "{\"documentType\":\"RESUME\"}", "bytes"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Invalid Request"));
    }

    @Test
    void recruiterCannotUpload() throws Exception {
      givenToken(RECRUITER);

      mvc.perform(upload(RECRUITER, "resume.pdf", "{\"documentType\":\"RESUME\"}", "pdf bytes"))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void anonymousCannotUpload() throws Exception {
      mvc.perform(multipart("/api/v1/documents"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(
        String token, String fileName, String metadataJson, String content) throws Exception {
      MockMultipartFile metadata =
          new MockMultipartFile(
              "metadata",
              "metadata.json",
              MediaType.APPLICATION_JSON_VALUE,
              metadataJson.getBytes());
      MockMultipartFile file =
          new MockMultipartFile(
              "file",
              fileName,
              "application/pdf",
              content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return multipart("/api/v1/documents")
          .file(metadata)
          .file(file)
          .header("Authorization", "Bearer " + token);
    }
  }

  // ------------------------------------------------------------------------- read

  @Nested
  class Read {

    @Test
    void ownerCanReadMetadata() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.get(DOCUMENT_ID, CANDIDATE_ID, null, false))
          .thenReturn(document(CANDIDATE_ID));

      mvc.perform(
              get("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.fileName").value("resume.pdf"));
    }

    @Test
    void anotherCandidatesDocumentIsNotFound() throws Exception {
      givenToken(OTHER_CANDIDATE);
      when(documentService.get(DOCUMENT_ID, OTHER_CANDIDATE_ID, null, false))
          .thenThrow(new DocumentNotFoundException("Document not found: " + DOCUMENT_ID));

      mvc.perform(
              get("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + OTHER_CANDIDATE))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void recruiterCannotReadACandidateDocument() throws Exception {
      givenToken(RECRUITER);
      when(documentService.get(eq(DOCUMENT_ID), isNull(), eq(ORG_ID), eq(false)))
          .thenThrow(new DocumentNotFoundException("Document not found: " + DOCUMENT_ID));

      mvc.perform(
              get("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + RECRUITER))
          .andExpect(status().isNotFound());
    }

    @Test
    void adminCanReadAnyDocument() throws Exception {
      givenToken(ADMIN);
      when(documentService.get(eq(DOCUMENT_ID), isNull(), isNull(), eq(true)))
          .thenReturn(document(CANDIDATE_ID));

      mvc.perform(
              get("/api/v1/documents/{id}", DOCUMENT_ID).header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isOk());
    }

    @Test
    void contentIsServedWithItsMediaTypeAndName() throws Exception {
      givenToken(CANDIDATE);
      InputStream stream = new ByteArrayInputStream("pdf".getBytes());
      when(documentService.getContent(eq(DOCUMENT_ID), eq(CANDIDATE_ID), isNull(), eq(false)))
          .thenReturn(new DocumentService.DocumentContent(document(CANDIDATE_ID), stream));

      mvc.perform(
              get("/api/v1/documents/{id}/content", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(content().contentType("application/pdf"))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.CONTENT_DISPOSITION,
                      org.hamcrest.Matchers.containsString("resume.pdf")))
          .andExpect(content().bytes("pdf".getBytes()));
    }

    @Test
    void documentsAreListedForTheTokenHolderOnly() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.listForCandidate(eq(CANDIDATE_ID), any()))
          .thenReturn(
              new org.springframework.data.domain.PageImpl<>(List.of(document(CANDIDATE_ID))));

      mvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].documentId").value(DOCUMENT_ID.toString()));

      verify(documentService).listForCandidate(eq(CANDIDATE_ID), any());
    }

    @Test
    void typeFilterAcceptsLowerCase() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.listForCandidateByType(CANDIDATE_ID, DocumentType.RESUME))
          .thenReturn(List.of(document(CANDIDATE_ID)));

      mvc.perform(
              get("/api/v1/documents/type/resume").header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].documentType").value("RESUME"));
    }

    @Test
    void unknownTypeFilterIsRejected() throws Exception {
      givenToken(CANDIDATE);

      mvc.perform(
              get("/api/v1/documents/type/passport").header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
  }

  // ----------------------------------------------------------------------- delete

  @Nested
  class Delete {

    @Test
    void ownerCanDelete() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.delete(DOCUMENT_ID, CANDIDATE_ID)).thenReturn(document(CANDIDATE_ID));

      mvc.perform(
              delete("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isNoContent());

      verify(documentService).delete(DOCUMENT_ID, CANDIDATE_ID);
    }

    @Test
    void nonOwnerIsForbidden() throws Exception {
      givenToken(OTHER_CANDIDATE);
      when(documentService.delete(eq(DOCUMENT_ID), eq(OTHER_CANDIDATE_ID)))
          .thenThrow(new DocumentForbiddenException("This document belongs to another candidate."));

      mvc.perform(
              delete("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + OTHER_CANDIDATE))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void missingDocumentIsNotFound() throws Exception {
      givenToken(CANDIDATE);
      when(documentService.delete(eq(DOCUMENT_ID), eq(CANDIDATE_ID)))
          .thenThrow(new DocumentNotFoundException("Document not found: " + DOCUMENT_ID));

      mvc.perform(
              delete("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + CANDIDATE))
          .andExpect(status().isNotFound());
    }

    @Test
    void adminIsNotAParticipantInDeletion() throws Exception {
      givenToken(ADMIN);

      mvc.perform(
              delete("/api/v1/documents/{id}", DOCUMENT_ID)
                  .header("Authorization", "Bearer " + ADMIN))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void aRecruiterTokenCarriesNoCandidateIdentity() throws Exception {
    givenToken(RECRUITER);
    // The service is asked with a null candidate id, never with one the request could have claimed:
    // the only candidate identity available to a recruiter token is none at all.
    when(documentService.get(eq(DOCUMENT_ID), isNull(), eq(ORG_ID), anyBoolean()))
        .thenThrow(new DocumentNotFoundException("Document not found: " + DOCUMENT_ID));

    mvc.perform(
            get("/api/v1/documents/{id}", DOCUMENT_ID)
                .header("Authorization", "Bearer " + RECRUITER))
        .andExpect(status().isNotFound());

    verify(documentService).get(eq(DOCUMENT_ID), isNull(), eq(ORG_ID), eq(false));
  }
}
