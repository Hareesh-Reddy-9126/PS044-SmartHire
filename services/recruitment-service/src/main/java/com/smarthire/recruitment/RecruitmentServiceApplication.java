package com.smarthire.recruitment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Recruitment Service entry point.
 *
 * <p>{@code @EnableDiscoveryClient} registers this service with Eureka so the gateway can resolve
 * {@code lb://RECRUITMENT-SERVICE}.
 *
 * <p>{@code @EnableFeignClients} activates the {@code infra} clients ({@code ApplicationClient},
 * {@code JobClient}) used to resolve ownership when an application is shortlisted.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.smarthire.recruitment.infra")
public class RecruitmentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RecruitmentServiceApplication.class, args);
  }
}
