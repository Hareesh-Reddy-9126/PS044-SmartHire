package com.smarthire.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Google Identity Services ID token submitted by the browser. */
@Schema(
    name = "GoogleLoginRequest",
    description = "Google Identity Services credential (a signed ID token)")
public class GoogleLoginRequest {

  @NotBlank
  @Size(max = 4096)
  @Schema(
      name = "credential",
      description = "The credential value from Google Identity Services",
      required = true,
      maxLength = 4096)
  private final String credential;

  @JsonCreator
  public GoogleLoginRequest(@JsonProperty("credential") String credential) {
    this.credential = credential;
  }

  public String credential() {
    return credential;
  }
}
