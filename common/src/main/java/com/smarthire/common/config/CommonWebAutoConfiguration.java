package com.smarthire.common.config;

import com.smarthire.common.web.CorrelationIdFilter;
import com.smarthire.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-registers SmartHire's shared servlet web infrastructure (correlation-id filter + Problem
 * Details advice). Gated on a servlet web application so it stays inert on reactive services such
 * as the gateway even if they depend on this library. Services opt in simply by depending on {@code
 * smarthire-common}; each bean is {@link ConditionalOnMissingBean} so a service can override.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class CommonWebAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean
  public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
