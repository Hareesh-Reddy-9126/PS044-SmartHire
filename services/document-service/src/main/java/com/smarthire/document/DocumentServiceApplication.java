package com.smarthire.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Document Service entry point.
 *
 * <p>{@code @EnableDiscoveryClient} registers this service with Eureka so the gateway can resolve
 * {@code lb://DOCUMENT-SERVICE}.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DocumentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(DocumentServiceApplication.class, args);
  }
}
