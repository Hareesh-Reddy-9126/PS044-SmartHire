package com.smarthire.auth.api;

import com.smarthire.auth.api.dto.UserEmailResponse;
import com.smarthire.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal user-directory endpoint for service-to-service calls.
 *
 * <p>Like notification-service's internal endpoints, this path is not routed by the API Gateway: it
 * exists for trusted services on the Docker network. It still requires a valid bearer token because
 * notification-service forwards the authenticated caller's token on its Feign calls.
 */
@RestController
@RequestMapping("/internal/v1/users")
@Tag(name = "Internal User Directory", description = "Service-to-service user lookups")
public class UserDirectoryInternalController {

  private final AuthService authService;

  public UserDirectoryInternalController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(summary = "Resolve a user's email address (internal)")
  @GetMapping("/{userId}/email")
  public UserEmailResponse email(@PathVariable UUID userId) {
    return new UserEmailResponse(userId, authService.emailOf(userId));
  }
}
