package com.smarthire.application.infra;

import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal context for the Feign resilience tests: the real Feign clients, the Resilience4j Spring
 * Boot aspects (configured from the service's own {@code application.yml}) and Jackson — but no
 * datasource, security or service discovery. Each test points a client at a {@link StubDownstream}
 * via {@code spring.cloud.openfeign.client.config.<client-name>.url}.
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({
  JacksonAutoConfiguration.class,
  HttpMessageConvertersAutoConfiguration.class,
  AopAutoConfiguration.class,
  FeignAutoConfiguration.class,
  CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class,
  BulkheadAutoConfiguration.class
})
@EnableFeignClients(clients = {JobClient.class, NotificationClient.class})
class FeignResilienceTestConfig {}
