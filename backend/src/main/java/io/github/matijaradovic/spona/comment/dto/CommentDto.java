package io.github.matijaradovic.spona.comment.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.matijaradovic.spona.comment.Comment;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;

public record CommentDto(
        UUID id,
        UUID postId,
        UUID parentId,
        ActorRefDto author,
        String content,
        Instant createdAt,
        ReactionSummaryDto reactions,
        long responseCount,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean viewerCanEdit,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean viewerCanDelete) {

    public static CommentDto from(Comment comment, long responseCount, Boolean canManage) {
        Comment parent = comment.getResponseToComment();
        return new CommentDto(comment.getId(), comment.getPost().getId(), parent == null ? null : parent.getId(),
                ActorRefDto.from(comment.getCreatedFor()), comment.getContent(), comment.getCreatedAt(),
                ReactionSummaryDto.empty(), parent == null ? responseCount : 0, canManage, canManage);
    }
}
