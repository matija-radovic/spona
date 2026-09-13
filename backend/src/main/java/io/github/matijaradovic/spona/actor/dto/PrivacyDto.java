package io.github.matijaradovic.spona.actor.dto;

import jakarta.validation.constraints.NotNull;

public record PrivacyDto(
        @NotNull(message = "A value is required.")
        Boolean showJobPostings) {}
