package io.github.matijaradovic.spona.user.dto;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ContactEntry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 120, message = "At most 120 characters.")
        String fullName,

        @Size(max = 500, message = "At most 500 characters.")
        String bio,

        String avatar,

        @Size(max = 120, message = "At most 120 characters.")
        String location,

        @Valid
        @Size(max = 15, message = "At most 15 entries.")
        List<ContactEntry> otherContactInfo) {}
