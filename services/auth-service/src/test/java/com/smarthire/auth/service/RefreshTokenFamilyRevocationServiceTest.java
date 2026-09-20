package com.smarthire.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.smarthire.auth.infra.RefreshTokenRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFamilyRevocationServiceTest {

  @Mock private RefreshTokenRepository repository;
  @InjectMocks private RefreshTokenFamilyRevocationService service;

  @Test
  void revokeFamilyDelegatesToRepository() {
    UUID familyId = UUID.randomUUID();

    service.revokeFamily(familyId);

    verify(repository).revokeFamily(familyId);
  }

  @Test
  void revokeFamilyUsesRequiredPropagationToAvoidNestedTransactionDeadlocks() throws Exception {
    Transactional annotation =
        RefreshTokenFamilyRevocationService.class
            .getMethod("revokeFamily", UUID.class)
            .getAnnotation(Transactional.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRED);
  }
}
