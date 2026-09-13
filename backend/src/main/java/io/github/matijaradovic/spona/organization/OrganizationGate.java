package io.github.matijaradovic.spona.organization;

import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.user.User;

@Component
public class OrganizationGate {

    private final OrganizationRepository organizations;
    private final RoleAssignmentRepository roles;

    public OrganizationGate(OrganizationRepository organizations, RoleAssignmentRepository roles) {
        this.organizations = organizations;
        this.roles = roles;
    }

    public Seat seat(String slug, User caller, Predicate<OrganizationRole> allowed, String refusal) {
        Seat seat = seat(slug, caller);
        if (!allowed.test(seat.role())) {
            throw new ForbiddenException(refusal);
        }
        return seat;
    }

    /** The role is null when the caller holds no live role in the organization. */
    public Seat seat(String slug, User caller) {
        Organization organization = organizations.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Organization not found."));
        return new Seat(organization, roles.findLiveRole(organization.getId(), caller.getId()).orElse(null));
    }

    public record Seat(Organization organization, OrganizationRole role) {
    }
}
