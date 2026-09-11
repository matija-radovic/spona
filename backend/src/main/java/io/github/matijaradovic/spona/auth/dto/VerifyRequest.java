package io.github.matijaradovic.spona.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank(message = "A token is required.")
        String token) {
}
