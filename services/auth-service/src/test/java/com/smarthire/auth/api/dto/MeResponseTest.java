package com.smarthire.auth.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.auth.service.AuthService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeResponseTest {

  @Test
  void identityViewsCopyRolesAtTheirBoundaries() {
    List<String> roles = new ArrayList<>(List.of("CANDIDATE"));

    MeResponse response = new MeResponse("user", "user@example.com", roles, null);
    AuthService.Profile profile =
        new AuthService.Profile(UUID.randomUUID(), "user@example.com", roles, null);

    roles.add("ADMIN");

    assertThat(response.roles()).containsExactly("CANDIDATE");
    assertThat(profile.roles()).containsExactly("CANDIDATE");
    assertThat(response.roles()).isUnmodifiable();
    assertThat(profile.roles()).isUnmodifiable();
  }
}
