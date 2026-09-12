package io.github.matijaradovic.spona.organization.member;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** `organization_member` is keyed by the pair, so the identifier is the pair */
public class OrganizationMemberId implements Serializable {

    private UUID user;

    private UUID organization;

    public OrganizationMemberId() {
    }

    public OrganizationMemberId(UUID user, UUID organization) {
        this.user = user;
        this.organization = organization;
    }

    public UUID getUser() {
        return user;
    }

    public UUID getOrganization() {
        return organization;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrganizationMemberId id)) {
            return false;
        }
        return Objects.equals(user, id.user) && Objects.equals(organization, id.organization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, organization);
    }
}
