package io.github.matijaradovic.spona.organization.dto;

import io.github.matijaradovic.spona.organization.member.OrganizationRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank(message = "A username or email address is required.")
        @Size(max = 254, message = "At most 254 characters.")
        String identifier,

        @NotNull(message = "A role is required.")
        OrganizationRole role) {
}
