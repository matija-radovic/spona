package io.github.matijaradovic.spona.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteOrganizationRequest(
        @NotBlank(message = "Type the slug to confirm.")
        String confirmSlug) {}
