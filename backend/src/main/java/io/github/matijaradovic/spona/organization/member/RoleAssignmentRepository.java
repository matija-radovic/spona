package io.github.matijaradovic.spona.organization.member;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    Optional<RoleAssignment> findByOrganizationIdAndUserIdAndRevokedAtIsNull(
            UUID organizationId, UUID userId);
}
