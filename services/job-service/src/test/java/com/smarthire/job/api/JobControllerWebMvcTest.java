package com.smarthire.job.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smarthire.job.domain.Job;
import com.smarthire.job.domain.JobStatus;
import com.smarthire.job.service.JobService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link JobController}. Runs on every host (no Docker/Postgres) with the
 * service layer mocked, so the controller and its {@code GET /api/v1/jobs} → 200 + JobResponse JSON
 * contract are actually executed here — the Testcontainers path ({@code JobApiIT}) is skipped on
 * Docker-less dev hosts, so this is what guards the controller wire format day to day.
 */
@WebMvcTest(JobController.class)
class JobControllerWebMvcTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private JobService jobService;

  @Test
  void listJobsReturns200WithTheJobResponseContract() throws Exception {
    Job job =
        new Job(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Acme Corp",
            "Backend Engineer",
            "Remote",
            JobStatus.OPEN);
    when(jobService.findAllJobs()).thenReturn(List.of(job));

    mvc.perform(get("/api/v1/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].jobId").value("11111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$[0].companyName").value("Acme Corp"))
        .andExpect(jsonPath("$[0].role").value("Backend Engineer"))
        .andExpect(jsonPath("$[0].location").value("Remote"))
        .andExpect(jsonPath("$[0].status").value("OPEN"));
  }
}
