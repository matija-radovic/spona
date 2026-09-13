package io.github.matijaradovic.spona.organization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.organization.Organization;

public record OrganizationDto(
        UUID id,
        ActorType actorType,
        Instant createdAt,
        String slug,
        String displayName,
        String legalName,
        String description,
        String website,
        String avatarUrl,
        String location,
        List<ContactEntry> otherContactInfo) {

    public static OrganizationDto from(Organization organization) {
        return new OrganizationDto(
                organization.getId(),
                organization.getActorType(),
                organization.getCreatedAt(),
                organization.getSlug(),
                organization.getDisplayName(),
                organization.getLegalName(),
                organization.getDescription(),
                organization.getWebsite(),
                organization.getAvatarUrl(),
                organization.getLocation(),
                List.copyOf(organization.getOtherContactInfo()));
    }
}
