package io.github.matijaradovic.spona.organization.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "A new owner is required.")
        UUID userId,

        @NotBlank(message = "Type the slug to confirm.")
        String confirmSlug) {}
