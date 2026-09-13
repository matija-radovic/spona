package io.github.matijaradovic.spona.organization.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    Optional<RoleAssignment> findByOrganizationIdAndUserIdAndRevokedAtIsNull(
            UUID organizationId, UUID userId);

    @Query("select r.organization.id from RoleAssignment r"
            + " where r.user.id = :userId and r.role = :role and r.revokedAt is null")
    List<UUID> findLiveOrganizationIds(UUID userId, OrganizationRole role);
}
