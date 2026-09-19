package com.smarthire.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.auth.service.InvalidTokenException;
import org.junit.jupiter.api.Test;

class AuthExceptionHandlerTest {

  @Test
  void invalidTokenDetailsAreGeneric() {
    var problem = new AuthExceptionHandler().handleInvalidToken(new InvalidTokenException("reuse"));

    assertThat(problem.getDetail()).isEqualTo("The supplied token is invalid.");
  }
}
