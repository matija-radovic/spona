package io.github.matijaradovic.spona.actor.activity.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.matijaradovic.spona.comment.Comment;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;

public sealed interface ActivityItemDto {

    record ActivityPostDto(
            String kind,
            UUID id,
            ActorRefDto author,
            String content,
            Instant createdAt,
            long commentCount,
            ReactionSummaryDto reactions,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanDelete) implements ActivityItemDto {
    }

    record ActivityCommentDto(
            String kind,
            UUID id,
            UUID postId,

            @JsonProperty("isReply")
            boolean isReply,

            ActorRefDto author,
            String content,
            Instant createdAt,
            ReactionSummaryDto reactions,
            long responseCount,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanDelete) implements ActivityItemDto {
    }

    static ActivityItemDto post(Post post, ActorRefDto author, long commentCount, ReactionSummaryDto reactions,
            Boolean canManage) {
        return new ActivityPostDto("post", post.getId(), author, post.getContent(), post.getCreatedAt(),
                commentCount, reactions, canManage, canManage);
    }

    static ActivityItemDto comment(Comment comment, ActorRefDto author, long responseCount,
            ReactionSummaryDto reactions, Boolean canManage) {
        boolean isReply = comment.getResponseToComment() != null;
        return new ActivityCommentDto("comment", comment.getId(), comment.getPost().getId(), isReply, author,
                comment.getContent(), comment.getCreatedAt(), reactions,
                isReply ? 0 : responseCount, canManage, canManage);
    }
}
