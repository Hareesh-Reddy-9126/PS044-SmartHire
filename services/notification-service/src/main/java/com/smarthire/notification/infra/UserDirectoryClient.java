package com.smarthire.notification.infra;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for auth-service's internal user directory, resolved through Eureka.
 *
 * <p>Notification rows carry a candidate id; the address an email is actually sent to must come
 * from the identity store rather than be derived from the id. The call forwards the authenticated
 * caller's token (see {@link FeignConfig}), so auth-service's normal authentication still applies.
 */
@FeignClient(name = "AUTH-SERVICE", path = "/internal/v1/users")
public interface UserDirectoryClient {

  @GetMapping("/{userId}/email")
  UserEmailResponse getEmail(@PathVariable("userId") UUID userId);

  /** One user's email, as returned by auth-service. */
  record UserEmailResponse(UUID userId, String email) {}
}
