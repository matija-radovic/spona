package io.github.matijaradovic.spona.organization.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.matijaradovic.spona.organization.invitation.InvitationStatus;
import io.github.matijaradovic.spona.organization.invitation.OrganizationInvitation;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.user.User;

public record InvitationDto(
        UUID id,
        InvitationStatus status,
        Invitee invitee,
        String email,
        OrganizationRole role,
        Instant createdAt,
        Instant resolvedAt,
        String invitedByUsername) {

    public record Invitee(String username, String fullName, String avatarUrl) {
    }

    public static InvitationDto from(OrganizationInvitation invitation) {
        User invitee = invitation.getInvitee();
        User inviter = invitation.getInvitedBy();
        return new InvitationDto(
                invitation.getId(),
                invitation.getStatus(),
                invitee == null ? null : new Invitee(invitee.getUsername(), invitee.getFullName(), invitee.getAvatarUrl()),
                invitee == null ? invitation.getInviteeEmail() : null,
                invitation.getRole(),
                invitation.getCreatedAt(),
                invitation.getResolvedAt(),
                inviter == null ? null : inviter.getUsername());
    }
}
