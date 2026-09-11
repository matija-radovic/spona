package io.github.matijaradovic.spona.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "A username or email address is required.")
        @Size(max = 254, message = "At most 254 characters.")
        String identifier,

        @NotBlank(message = "A password is required.")
        @Size(max = 64, message = "At most 64 characters.")
        String password) {
}
