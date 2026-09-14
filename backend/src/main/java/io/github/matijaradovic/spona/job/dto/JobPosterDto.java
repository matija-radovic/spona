package io.github.matijaradovic.spona.job.dto;

import java.util.UUID;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

public sealed interface JobPosterDto {

    record OrganizationPoster(ActorType actorType, UUID id, String name, String handle, String avatarUrl)
            implements JobPosterDto {
    }

    /** No id, name or handle: a person's posting publishes no identity. */
    record PersonPoster(ActorType actorType) implements JobPosterDto {
    }

    static JobPosterDto from(Actor actor) {
        return switch (actor) {
            case Organization organization -> {
                ActorRefDto ref = ActorRefDto.from(organization);
                yield new OrganizationPoster(ref.actorType(), ref.id(), ref.name(), ref.handle(), ref.avatarUrl());
            }
            case User user -> new PersonPoster(user.getActorType());
            default -> throw new IllegalArgumentException("Unknown actor subtype " + actor.getClass());
        };
    }
}
