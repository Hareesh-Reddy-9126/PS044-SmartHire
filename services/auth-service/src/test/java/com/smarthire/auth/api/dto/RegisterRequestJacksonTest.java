package com.smarthire.auth.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;

/** Test to verify Jackson can deserialize RegisterRequest and LoginRequest records. */
class RegisterRequestJacksonTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .registerModule(new ParameterNamesModule());

  @Test
  void registerRequestDeserializesFromValidJson() throws Exception {
    String json =
        """
        {
          "email": "test@example.com",
          "password": "Test1234567890!"
        }
        """;

    RegisterRequest request = objectMapper.readValue(json, RegisterRequest.class);

    assertThat(request.email()).isEqualTo("test@example.com");
    assertThat(request.password()).isEqualTo("Test1234567890!");
  }

  @Test
  void loginRequestDeserializesFromValidJson() throws Exception {
    String json =
        """
        {
          "email": "test@example.com",
          "password": "Test1234567890!"
        }
        """;

    LoginRequest request = objectMapper.readValue(json, LoginRequest.class);

    assertThat(request.email()).isEqualTo("test@example.com");
    assertThat(request.password()).isEqualTo("Test1234567890!");
  }

  @Test
  void registerRequestFailsWithMissingFields() throws Exception {
    String json = "{}";

    // Jackson creates a record with null fields instead of failing
    RegisterRequest request = objectMapper.readValue(json, RegisterRequest.class);
    assertThat(request.email()).isNull();
    assertThat(request.password()).isNull();
  }
}
