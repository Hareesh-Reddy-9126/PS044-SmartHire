package com.smarthire.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login request. */
@Schema(name = "LoginRequest", description = "Request payload for user login")
public class LoginRequest {

  @NotBlank
  @Size(max = 255)
  @Schema(
      name = "email",
      description = "User's email address",
      example = "user@example.com",
      required = true,
      maxLength = 255)
  private final String email;

  @NotBlank
  @Size(max = 128)
  @Schema(
      name = "password",
      description = "User's password",
      example = "securePassword123",
      required = true,
      maxLength = 128)
  private final String password;

  @JsonCreator
  public LoginRequest(
      @JsonProperty("email") String email, @JsonProperty("password") String password) {
    this.email = email;
    this.password = password;
  }

  public String email() {
    return email;
  }

  public String password() {
    return password;
  }
}
