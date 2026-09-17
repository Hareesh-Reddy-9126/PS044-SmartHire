package com.smarthire.job.api;

import com.smarthire.job.api.dto.JobResponse;
import com.smarthire.job.service.JobService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Job listing endpoint. Versioned base path {@code /api/v1} from day one (governance §11). */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  @GetMapping
  public List<JobResponse> listJobs() {
    return jobService.findAllJobs().stream().map(JobResponse::from).toList();
  }
}
