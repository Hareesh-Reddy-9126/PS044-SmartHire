package com.smarthire.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Auth-service entry point: identity provider, RS256 JWT issuer + JWKS, RBAC (ADR-0009/0010). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
