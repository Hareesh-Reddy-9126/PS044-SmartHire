package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarthire.auth.config.RegistrationProperties;
import org.junit.jupiter.api.Test;

/** Security rules for administrator self-registration. */
class RegistrationPolicyTest {

  @Test
  void blankConfigurationDisablesAdminRegistration() {
    RegistrationPolicy policy = new RegistrationPolicy(new RegistrationProperties("  "));

    assertThat(policy.adminRegistrationEnabled()).isFalse();
    assertThat(policy.isAdminInviteCodeValid("anything")).isFalse();
    assertThat(policy.isAdminInviteCodeValid(null)).isFalse();
  }

  @Test
  void matchingInvitationCodeIsAccepted() {
    RegistrationPolicy policy = new RegistrationPolicy(new RegistrationProperties("deploy-code"));

    assertThat(policy.adminRegistrationEnabled()).isTrue();
    assertThat(policy.isAdminInviteCodeValid("deploy-code")).isTrue();
    assertThat(policy.isAdminInviteCodeValid("  deploy-code  ")).isTrue();
  }

  @Test
  void wrongOrMissingInvitationCodeIsRejected() {
    RegistrationPolicy policy = new RegistrationPolicy(new RegistrationProperties("deploy-code"));

    assertThat(policy.isAdminInviteCodeValid("wrong-code")).isFalse();
    assertThat(policy.isAdminInviteCodeValid("")).isFalse();
    assertThat(policy.isAdminInviteCodeValid("   ")).isFalse();
    assertThat(policy.isAdminInviteCodeValid(null)).isFalse();
  }
}
