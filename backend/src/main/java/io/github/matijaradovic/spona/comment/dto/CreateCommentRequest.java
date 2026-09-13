package io.github.matijaradovic.spona.comment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        String content,

        @NotNull(message = "An actor to publish as is required.")
        UUID createdForId,

        UUID parentId) {}
