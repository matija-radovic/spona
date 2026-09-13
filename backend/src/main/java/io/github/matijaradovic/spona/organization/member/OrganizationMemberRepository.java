package io.github.matijaradovic.spona.organization.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, OrganizationMemberId> {

    long countByOrganization_Id(UUID organizationId);

    @Query("select m from OrganizationMember m join fetch m.user u"
            + " where m.organization.id = :organizationId order by m.joinedAt, u.username")
    List<OrganizationMember> findRoster(UUID organizationId);

    @Query("select m from OrganizationMember m join fetch m.organization o"
            + " where m.user.id = :userId order by m.joinedAt, o.slug")
    List<OrganizationMember> findMemberships(UUID userId);

    /** newest accepted invitation is the one that produced the newestt membership */
    @Query(value = "select invited_by_id from organization_invitations"
            + " where organization_id = :organizationId and invitee_user_id = :userId and status = 'ACCEPTED'"
            + " order by resolved_at desc limit 1", nativeQuery = true)
    Optional<UUID> findInviterId(UUID organizationId, UUID userId);
}
