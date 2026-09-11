package io.github.matijaradovic.spona.auth.dto;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ContactEntry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "A username is required.")
        @Size(min = 3, max = 30, message = "Between 3 and 30 characters.")
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "Lowercase letters, digits, '-' and '_' only.")
        String username,

        @NotBlank(message = "An email address is required.")
        @Email(message = "That is not a valid email address.")
        @Size(max = 254, message = "At most 254 characters.")
        String email,

        @NotBlank(message = "A password is required.")
        @Size(min = 8, max = 64, message = "Between 8 and 64 characters.")
        @Pattern(regexp = "^[A-Za-z0-9~!@#$%^&*()_+={}|\\[\\]:;\"'<,>.?/]+$",
                message = "Letters, digits and common punctuation only.")
        String password,

        @Size(max = 120, message = "At most 120 characters.")
        String fullName,

        @Size(max = 500, message = "At most 500 characters.")
        String bio,

        @Size(max = 2048, message = "At most 2048 characters.")
        String avatarUrl,

        @Size(max = 120, message = "At most 120 characters.")
        String location,

        @Valid
        @Size(max = 15, message = "At most 15 entries.")
        List<ContactEntry> otherContactInfo) {}
