package io.github.matijaradovic.spona.post.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.user.User;

public record PostDto(
        UUID id,
        ActorRefDto author,
        String content,
        Instant createdAt,
        long commentCount,
        ReactionSummaryDto reactions,
        String authorBio,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean authorFollowedByViewer,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean viewerCanEdit,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean viewerCanDelete) {

    public static PostDto from(Post post, long commentCount, Boolean authorFollowedByViewer, Boolean canManage) {
        Actor author = post.getCreatedFor();
        String authorBio = switch (author) {
            case User user -> user.getBio();
            case Organization organization -> organization.getDescription();
            default -> throw new IllegalArgumentException("Unknown actor subtype " + author.getClass());
        };
        return new PostDto(post.getId(), ActorRefDto.from(author), post.getContent(), post.getCreatedAt(),
                commentCount, ReactionSummaryDto.empty(), authorBio, authorFollowedByViewer, canManage, canManage);
    }
}
