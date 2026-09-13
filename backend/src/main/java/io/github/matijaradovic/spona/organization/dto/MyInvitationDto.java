package io.github.matijaradovic.spona.organization.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.organization.invitation.OrganizationInvitation;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.user.User;

public record MyInvitationDto(
        UUID id,
        ActorRefDto organization,
        OrganizationRole role,
        ActorRefDto invitedBy,
        Instant createdAt) {

    public static MyInvitationDto from(OrganizationInvitation invitation) {
        User inviter = invitation.getInvitedBy();
        return new MyInvitationDto(invitation.getId(), ActorRefDto.from(invitation.getOrganization()),
                invitation.getRole(), inviter == null ? null : ActorRefDto.from(inviter),
                invitation.getCreatedAt());
    }
}
