package io.github.matijaradovic.spona.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        @NotBlank(message = "A password is required.")
        @Size(min = 8, max = 64, message = "Between 8 and 64 characters.")
        @Pattern(regexp = "^[A-Za-z0-9~!@#$%^&*()_+={}|\\[\\]:;\"'<,>.?/]+$",
                message = "Letters, digits and common punctuation only.")
        String newPassword) {}
