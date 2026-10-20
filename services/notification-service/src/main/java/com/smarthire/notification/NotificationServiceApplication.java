package com.smarthire.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Notification Service entry point.
 *
 * <p>{@code @EnableDiscoveryClient} registers this service with Eureka so the gateway can resolve
 * {@code lb://NOTIFICATION-SERVICE}.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
