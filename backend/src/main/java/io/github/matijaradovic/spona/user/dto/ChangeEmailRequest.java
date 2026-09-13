package io.github.matijaradovic.spona.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank(message = "An email address is required.")
        @Email(message = "That is not a valid email address.")
        @Size(max = 254, message = "At most 254 characters.")
        String email,

        @NotBlank(message = "Current password is required.")
        String currentPassword) {}
