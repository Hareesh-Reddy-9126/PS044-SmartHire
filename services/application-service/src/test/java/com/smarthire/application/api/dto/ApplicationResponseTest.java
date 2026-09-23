package com.smarthire.application.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.application.domain.Application;
import com.smarthire.application.domain.ApplicationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApplicationResponse} DTO mapping. */
class ApplicationResponseTest {

  @Test
  void mapsApplicationToResponse() {
    UUID applicationId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    String resumeLink = "https://example.com/resume.pdf";

    Application application =
        new Application(applicationId, jobId, candidateId, resumeLink, ApplicationStatus.APPLIED);

    ApplicationResponse response = ApplicationResponse.from(application);

    assertThat(response.applicationId()).isEqualTo(applicationId);
    assertThat(response.jobId()).isEqualTo(jobId);
    assertThat(response.candidateId()).isEqualTo(candidateId);
    assertThat(response.resumeLink()).isEqualTo(resumeLink);
    assertThat(response.status()).isEqualTo("APPLIED");
  }
}
