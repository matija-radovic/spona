package io.github.matijaradovic.spona.comment.dto;

public record CommentContextDto(
        CommentDto comment,
        CommentDto parent,
        long siblingIndex,
        long siblingTotal) {
}
