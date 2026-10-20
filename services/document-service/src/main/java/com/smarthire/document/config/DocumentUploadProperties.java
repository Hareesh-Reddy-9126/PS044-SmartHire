package com.smarthire.document.config;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Upload constraints for document-service, bound from {@code smarthire.document.upload}.
 *
 * <p>These live here rather than in the controller so the service layer — the only layer that may
 * be reached from more than one entry point — is what actually enforces them. A limit that exists
 * only in a controller is a limit that a future caller can skip.
 */
@ConfigurationProperties(prefix = "smarthire.document.upload")
@Validated
public record DocumentUploadProperties(
    @NotNull DataSize maxFileSize, @NotNull List<String> allowedContentTypes) {

  public DocumentUploadProperties {
    allowedContentTypes =
        allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
  }
}
