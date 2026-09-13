package io.github.matijaradovic.spona.organization.dto;

import java.time.Instant;

import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;

public record MyOrganizationDto(
        ActorRefDto organization,
        OrganizationRole role,
        Instant joinedAt,
        Instant roleUpdatedAt,
        ActorRefDto invitedBy,
        long memberCount) {

    public static MyOrganizationDto from(OrganizationMember membership, RoleAssignment live, ActorRefDto invitedBy,
            long memberCount) {
        return new MyOrganizationDto(
                ActorRefDto.from(membership.getOrganization()),
                live.getRole(),
                membership.getJoinedAt(),
                live.getAssignedAt(),
                invitedBy,
                memberCount);
    }
}
