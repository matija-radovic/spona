package io.github.matijaradovic.spona.actor.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

public sealed interface ProfileDto {

    record ViewerFields(Boolean isFollowedByViewer, Boolean canEdit, Boolean canManageJobPostings) {
        public static ViewerFields guest() {
            return new ViewerFields(null, null, null);
        }
    }

    record UserProfile(
            ActorType actorType,
            UUID id,
            String username,
            String fullName,
            String bio,
            String avatarUrl,
            String location,
            List<ContactEntry> otherContactInfo,
            long followers,
            long following,
            Instant createdAt,

            @JsonProperty("isFollowedByViewer")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean isFollowedByViewer,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanManageJobPostings) implements ProfileDto {
    }

    /** only user can follow, so no following field. */
    record OrganizationProfile(
            ActorType actorType,
            UUID id,
            String slug,
            String displayName,
            String description,
            String website,
            String avatarUrl,
            String location,
            List<ContactEntry> otherContactInfo,
            long followers,
            Instant createdAt,

            @JsonProperty("isFollowedByViewer")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean isFollowedByViewer,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanManageJobPostings) implements ProfileDto {
    }

    static ProfileDto from(User user, long followers, long following, ViewerFields viewer) {
        return new UserProfile(
                user.getActorType(),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getLocation(),
                List.copyOf(user.getOtherContactInfo()),
                followers,
                following,
                user.getCreatedAt(),
                viewer.isFollowedByViewer(),
                viewer.canEdit(),
                viewer.canManageJobPostings());
    }

    static ProfileDto from(Organization organization, long followers, ViewerFields viewer) {
        return new OrganizationProfile(
                organization.getActorType(),
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName(),
                organization.getDescription(),
                organization.getWebsite(),
                organization.getAvatarUrl(),
                organization.getLocation(),
                List.copyOf(organization.getOtherContactInfo()),
                followers,
                organization.getCreatedAt(),
                viewer.isFollowedByViewer(),
                viewer.canEdit(),
                viewer.canManageJobPostings());
    }
}
