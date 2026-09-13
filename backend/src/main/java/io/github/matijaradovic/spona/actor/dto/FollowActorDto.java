package io.github.matijaradovic.spona.actor.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;

public record FollowActorDto(
        ActorType actorType,
        UUID id,
        String name,
        String handle,
        String avatarUrl,

        @JsonProperty("isFollowedByViewer")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean isFollowedByViewer) {

    public static FollowActorDto from(ActorRefDto ref, Boolean isFollowedByViewer) {
        return new FollowActorDto(ref.actorType(), ref.id(), ref.name(), ref.handle(), ref.avatarUrl(),
                isFollowedByViewer);
    }
}
