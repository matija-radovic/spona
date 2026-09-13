package io.github.matijaradovic.spona.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Your password is required.")
        String password) {}
