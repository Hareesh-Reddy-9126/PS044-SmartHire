package com.smarthire.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Job service: owns the Job aggregate and the {@code job_svc} schema. */
@SpringBootApplication
public class JobServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobServiceApplication.class, args);
  }
}
