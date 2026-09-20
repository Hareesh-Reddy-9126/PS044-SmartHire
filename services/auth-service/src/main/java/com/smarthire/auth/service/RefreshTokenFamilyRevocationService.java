package com.smarthire.auth.service;

import com.smarthire.auth.infra.RefreshTokenRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes all active refresh tokens in a family upon detected reuse or logout. Participates in the
 * caller's transaction so family revocation is committed alongside the failure handling without
 * relying on a nested transaction.
 */
@Service
public class RefreshTokenFamilyRevocationService {

  private final RefreshTokenRepository repository;

  public RefreshTokenFamilyRevocationService(RefreshTokenRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void revokeFamily(UUID familyId) {
    repository.revokeFamily(familyId);
  }
}
