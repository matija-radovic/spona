package io.github.matijaradovic.spona.actor.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

public sealed interface ActorSearchItemDto {

    record UserItem(
            ActorType actorType,
            UUID id,
            String name,
            String handle,
            String avatarUrl,
            String bio,
            long followers,
            long following,

            @JsonProperty("isFollowedByViewer")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean isFollowedByViewer) implements ActorSearchItemDto {
    }

    record OrganizationItem(
            ActorType actorType,
            UUID id,
            String name,
            String handle,
            String avatarUrl,
            String description,
            long followers,

            @JsonProperty("isFollowedByViewer")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean isFollowedByViewer) implements ActorSearchItemDto {
    }

    static ActorSearchItemDto from(User user, long followers, long following, Boolean isFollowedByViewer) {
        return new UserItem(user.getActorType(), user.getId(), user.getFullName(), user.getUsername(),
                user.getAvatarUrl(), user.getBio(), followers, following, isFollowedByViewer);
    }

    static ActorSearchItemDto from(Organization organization, long followers, Boolean isFollowedByViewer) {
        return new OrganizationItem(organization.getActorType(), organization.getId(), organization.getDisplayName(),
                organization.getSlug(), organization.getAvatarUrl(), organization.getDescription(), followers,
                isFollowedByViewer);
    }
}
