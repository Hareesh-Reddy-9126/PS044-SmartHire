package com.smarthire.auth.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for auth-service. Registers ParameterNamesModule to support
 * record/constructor-based deserialization and JavaTimeModule for Java 8 date/time types (Instant,
 * LocalDateTime, etc.).
 */
@Configuration
public class JacksonConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> builder.modules(new ParameterNamesModule(), new JavaTimeModule());
  }
}
