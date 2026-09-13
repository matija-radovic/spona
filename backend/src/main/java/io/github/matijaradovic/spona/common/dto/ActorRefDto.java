package io.github.matijaradovic.spona.common.dto;

import java.util.UUID;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

public record ActorRefDto(ActorType actorType, UUID id, String name, String handle, String avatarUrl) {
    public static ActorRefDto from(User user) {
        return new ActorRefDto(user.getActorType(), user.getId(), user.getFullName(), user.getUsername(),
                user.getAvatarUrl());
    }

    public static ActorRefDto from(Organization organization) {
        return new ActorRefDto(organization.getActorType(), organization.getId(), organization.getDisplayName(),
                organization.getSlug(), organization.getAvatarUrl());
    }

    public static ActorRefDto from(Actor actor) {
        return switch (actor) {
            case User user -> from(user);
            case Organization organization -> from(organization);
            default -> throw new IllegalArgumentException("Unknown actor subtype " + actor.getClass());
        };
    }
}
