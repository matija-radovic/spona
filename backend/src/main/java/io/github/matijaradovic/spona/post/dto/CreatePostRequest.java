package io.github.matijaradovic.spona.post.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreatePostRequest(
        String content,

        @NotNull(message = "An actor to publish as is required.")
        UUID createdForId) {}
