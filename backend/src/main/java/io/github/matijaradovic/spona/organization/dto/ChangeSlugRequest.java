package io.github.matijaradovic.spona.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeSlugRequest(
        @NotBlank(message = "A slug is required.")
        @Size(min = 3, max = 40, message = "Between 3 and 40 characters.")
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "Lowercase letters, digits, '-' and '_' only.")
        String slug) {}
