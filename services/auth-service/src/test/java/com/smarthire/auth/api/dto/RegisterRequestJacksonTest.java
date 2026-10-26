package com.smarthire.auth.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Test to verify Jackson can deserialize RegisterRequest and LoginRequest records. */
class RegisterRequestJacksonTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

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
    assertThat(request.role()).isNull();
    assertThat(request.organizationName()).isNull();
    assertThat(request.adminInviteCode()).isNull();
  }

  @Test
  void recruiterRegistrationDeserializesRoleAndOrganization() throws Exception {
    String json =
        """
        {
          "email": "hire@acme.example",
          "password": "Test1234567890!",
          "role": "RECRUITER",
          "organizationName": "Acme Corp"
        }
        """;

    RegisterRequest request = objectMapper.readValue(json, RegisterRequest.class);

    assertThat(request.role()).isEqualTo("RECRUITER");
    assertThat(request.organizationName()).isEqualTo("Acme Corp");
  }

  @Test
  void adminRegistrationDeserializesInviteCode() throws Exception {
    String json =
        """
        {
          "email": "ops@smarthire.example",
          "password": "Test1234567890!",
          "role": "ADMIN",
          "adminInviteCode": "deployment-code"
        }
        """;

    RegisterRequest request = objectMapper.readValue(json, RegisterRequest.class);

    assertThat(request.role()).isEqualTo("ADMIN");
    assertThat(request.adminInviteCode()).isEqualTo("deployment-code");
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
