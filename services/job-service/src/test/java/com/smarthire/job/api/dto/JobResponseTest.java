package com.smarthire.job.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobResponseTest {

  @Test
  void mapsEverySourceField() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID ownerOrgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    Job job = new Job(id, "Acme Corp", "Backend Engineer", "Remote", JobStatus.OPEN, ownerOrgId);

    JobResponse dto = JobResponse.from(job);

    assertThat(dto.jobId()).isEqualTo(id);
    assertThat(dto.companyName()).isEqualTo("Acme Corp");
    assertThat(dto.role()).isEqualTo("Backend Engineer");
    assertThat(dto.location()).isEqualTo("Remote");
    assertThat(dto.status()).isEqualTo("OPEN");
    assertThat(dto.ownerOrgId()).isEqualTo(ownerOrgId);
  }
}
