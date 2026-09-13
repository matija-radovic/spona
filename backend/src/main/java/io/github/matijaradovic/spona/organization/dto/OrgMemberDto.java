package io.github.matijaradovic.spona.organization.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.user.User;

public record OrgMemberDto(
        UUID userId,
        String username,
        String fullName,
        String avatarUrl,
        OrganizationRole role,
        Instant joinedAt) {

    public static OrgMemberDto from(OrganizationMember membership, OrganizationRole role) {
        User user = membership.getUser();
        return new OrgMemberDto(user.getId(), user.getUsername(), user.getFullName(), user.getAvatarUrl(), role,
                membership.getJoinedAt());
    }
}
