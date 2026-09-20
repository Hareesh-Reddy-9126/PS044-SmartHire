package com.smarthire.auth.infra;

import com.smarthire.auth.domain.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence port for {@link Organization}. Lives in {@code infra} per governance §3. */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
