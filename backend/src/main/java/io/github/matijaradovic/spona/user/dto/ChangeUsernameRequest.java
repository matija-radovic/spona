package io.github.matijaradovic.spona.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank(message = "A username is required.")
        @Size(min = 3, max = 30, message = "Between 3 and 30 characters.")
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "Lowercase letters, digits, '-' and '_' only.")
        String username) {}
