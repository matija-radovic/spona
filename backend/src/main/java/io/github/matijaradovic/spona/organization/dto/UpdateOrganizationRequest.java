package io.github.matijaradovic.spona.organization.dto;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ContactEntry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "A display name is required.")
        @Size(max = 120, message = "At most 120 characters.")
        String displayName,

        @NotBlank(message = "A legal name is required.")
        @Size(max = 160, message = "At most 160 characters.")
        String legalName,

        @Size(max = 1000, message = "At most 1000 characters.")
        String description,

        @Size(max = 200, message = "At most 200 characters.")
        String website,

        String avatar,

        @Size(max = 120, message = "At most 120 characters.")
        String location,

        @Valid
        @Size(max = 15, message = "At most 15 entries.")
        List<ContactEntry> otherContactInfo) {}
