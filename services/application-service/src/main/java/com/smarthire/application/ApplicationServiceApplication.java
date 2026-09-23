package com.smarthire.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Application Service entry point.
 *
 * <p>{@code @EnableDiscoveryClient} registers this service with Eureka so the gateway can resolve
 * {@code lb://APPLICATION-SERVICE}.
 *
 * <p>{@code @EnableFeignClients} activates the {@link com.smarthire.application.infra.JobClient}
 * for inter-service communication with Job Service.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(
    basePackages = {"com.smarthire.application.service", "com.smarthire.application.infra"})
public class ApplicationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApplicationServiceApplication.class, args);
  }
}
