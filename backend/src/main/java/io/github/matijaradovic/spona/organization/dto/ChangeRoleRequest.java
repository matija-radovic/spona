package io.github.matijaradovic.spona.organization.dto;

import io.github.matijaradovic.spona.organization.member.OrganizationRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "A role is required.") 
        OrganizationRole role) {
}
