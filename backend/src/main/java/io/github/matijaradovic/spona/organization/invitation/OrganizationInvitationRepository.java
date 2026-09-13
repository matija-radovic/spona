package io.github.matijaradovic.spona.organization.invitation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {

    Optional<OrganizationInvitation> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndInvitee_IdAndStatus(UUID organizationId, UUID inviteeId,
            InvitationStatus status);

    List<OrganizationInvitation> findByInvitee_IdAndStatus(UUID inviteeId, InvitationStatus status);

    @Query("select i from OrganizationInvitation i left join fetch i.invitee left join fetch i.invitedBy"
            + " where i.organization.id = :organizationId and i.status in :statuses and i.dismissedAt is null"
            + " order by i.createdAt desc, i.id desc")
    List<OrganizationInvitation> findListed(UUID organizationId, Collection<InvitationStatus> statuses);

    /** Bound to the user, or unbound and sent to their verified address */
    @Query("select i from OrganizationInvitation i join fetch i.organization left join fetch i.invitedBy"
            + " where i.status = :status and (i.invitee.id = :userId"
            + " or (i.invitee is null and lower(i.inviteeEmail) = lower(:email)))"
            + " order by i.createdAt desc, i.id desc")
    List<OrganizationInvitation> findAddressedTo(UUID userId, String email, InvitationStatus status);

    @Query("select i from OrganizationInvitation i"
            + " where i.status = :status and i.invitee is null and lower(i.inviteeEmail) = lower(:email)")
    List<OrganizationInvitation> findUnbound(String email, InvitationStatus status);
}
