package io.github.matijaradovic.spona.organization.dto;

import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;

public record OrgSettingsDto(
        UUID id,
        String slug,
        String displayName,
        String legalName,
        String description,
        String website,
        String avatarUrl,
        String location,
        List<ContactEntry> otherContactInfo,
        PrivacyDto privacy,
        OrganizationRole viewerRole,
        long memberCount) {

    public static OrgSettingsDto from(Organization organization, PrivacyDto privacy,
            OrganizationRole viewerRole, long memberCount) {
        return new OrgSettingsDto(
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName(),
                organization.getLegalName(),
                organization.getDescription(),
                organization.getWebsite(),
                organization.getAvatarUrl(),
                organization.getLocation(),
                List.copyOf(organization.getOtherContactInfo()),
                privacy,
                viewerRole,
                memberCount);
    }
}
