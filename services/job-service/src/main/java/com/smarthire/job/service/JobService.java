package com.smarthire.job.service;

import com.smarthire.job.domain.Job;
import com.smarthire.job.infra.JobRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for jobs. Inc 0 exposes a read-only listing; write/search arrive at Inc 2.
 */
@Service
public class JobService {

  private final JobRepository jobRepository;

  public JobService(JobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @Transactional(readOnly = true)
  public List<Job> findAllJobs() {
    return jobRepository.findAll();
  }
}
