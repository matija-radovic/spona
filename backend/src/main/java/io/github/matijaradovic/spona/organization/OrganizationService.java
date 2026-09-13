package io.github.matijaradovic.spona.organization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.ReservedHandles;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.OrganizationGate.Seat;
import io.github.matijaradovic.spona.organization.dto.CreateOrganizationRequest;
import io.github.matijaradovic.spona.organization.dto.OrgSettingsDto;
import io.github.matijaradovic.spona.organization.dto.OrganizationDto;
import io.github.matijaradovic.spona.organization.dto.TransferOwnershipRequest;
import io.github.matijaradovic.spona.organization.dto.UpdateOrganizationRequest;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.storage.AvatarService;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class OrganizationService {

    private static final String NOT_A_MEMBER = "You are not a member of this organization.";
    private static final String NOT_AN_ADMIN = "Only the owner or an admin may change this organization.";
    private static final String NOT_THE_OWNER = "Only the owner may do this.";

    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final RoleAssignmentRepository roles;
    private final UserRepository users;
    private final PrivacyService privacy;
    private final AvatarService avatars;
    private final OrganizationGate gate;

    public OrganizationService(OrganizationRepository organizations, OrganizationMemberRepository members,
            RoleAssignmentRepository roles, UserRepository users, PrivacyService privacy, AvatarService avatars,
            OrganizationGate gate) {
        this.organizations = organizations;
        this.members = members;
        this.roles = roles;
        this.users = users;
        this.privacy = privacy;
        this.avatars = avatars;
        this.gate = gate;
    }

    @Transactional
    public OrganizationDto create(User caller, CreateOrganizationRequest request) {
        User founder = users.findById(caller.getId())
                .orElseThrow(() -> new UnauthorizedException("Session not valid. Sign in again."));
        requireFreeSlug(request.slug());

        Organization organization = new Organization(request.slug(),
                Strings.trimToEmpty(request.displayName()), Strings.trimToEmpty(request.legalName()));
        organization.setDescription(Strings.trimToNull(request.description()));
        organization.setWebsite(Strings.trimToNull(request.website()));
        organization.setLocation(Strings.trimToNull(request.location()));
        organization.setOtherContactInfo(request.otherContactInfo());
        if (request.avatar() != null) {
            organization.setAvatarUrl(avatars.store(request.avatar()));
        }

        organizations.saveAndFlush(organization);
        members.save(new OrganizationMember(founder, organization));
        roles.save(new RoleAssignment(founder, organization, OrganizationRole.OWNER, founder));
        return OrganizationDto.from(organization);
    }

    @Transactional(readOnly = true)
    public OrgSettingsDto settings(String slug, User caller) {
        return settingsOf(gate.seat(slug, caller, PublishAuthority::canReadSettings, NOT_A_MEMBER));
    }

    @Transactional
    public OrgSettingsDto updateProfile(String slug, User caller, UpdateOrganizationRequest request) {
        Seat seat = gate.seat(slug, caller, PublishAuthority::canEditProfile, NOT_AN_ADMIN);
        Organization organization = seat.organization();
        organization.setDisplayName(Strings.trimToEmpty(request.displayName()));
        organization.setLegalName(Strings.trimToEmpty(request.legalName()));
        organization.setDescription(Strings.trimToNull(request.description()));
        organization.setWebsite(Strings.trimToNull(request.website()));
        organization.setLocation(Strings.trimToNull(request.location()));
        organization.setOtherContactInfo(request.otherContactInfo());
        if (request.avatar() != null) {
            organization.setAvatarUrl(avatars.replace(organization.getAvatarUrl(), request.avatar()));
        }
        return settingsOf(seat);
    }

    @Transactional
    public OrgSettingsDto changeSlug(String slug, User caller, String nextSlug) {
        Seat seat = gate.seat(slug, caller, PublishAuthority::canManageOrganization, NOT_THE_OWNER);
        Organization organization = seat.organization();
        if (!nextSlug.equals(organization.getSlug())) {
            requireFreeSlug(nextSlug);
            organization.setSlug(nextSlug);
        }
        return settingsOf(seat);
    }

    @Transactional
    public void transferOwnership(String slug, User caller, TransferOwnershipRequest request) {
        Organization organization = gate.seat(slug, caller, PublishAuthority::canManageOrganization, NOT_THE_OWNER)
                .organization();
        requireConfirmation(organization, request.confirmSlug());

        RoleAssignment successor = liveAssignment(organization, request.userId())
                .filter(row -> row.getRole() == OrganizationRole.ADMIN)
                .orElseThrow(() -> UnprocessableException.on("userId",
                        "Ownership passes only to an admin of this organization."));
        RoleAssignment owner = liveAssignment(organization, caller.getId())
                .orElseThrow(() -> new ForbiddenException(NOT_THE_OWNER));

        Instant now = Instant.now();
        owner.revoke(now);
        successor.revoke(now);
        roles.flush();

        User previousOwner = owner.getUser();
        roles.save(new RoleAssignment(previousOwner, organization, OrganizationRole.ADMIN, previousOwner));
        roles.save(new RoleAssignment(successor.getUser(), organization, OrganizationRole.OWNER, previousOwner));
    }

    @Transactional
    public void delete(String slug, User caller, String confirmSlug) {
        Organization organization = gate.seat(slug, caller, PublishAuthority::canManageOrganization, NOT_THE_OWNER)
                .organization();
        requireConfirmation(organization, confirmSlug);

        avatars.discard(organization.getAvatarUrl());
        organizations.delete(organization);
    }

    private Optional<RoleAssignment> liveAssignment(Organization organization, UUID userId) {
        return roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(organization.getId(), userId);
    }

    private OrgSettingsDto settingsOf(Seat seat) {
        Organization organization = seat.organization();
        return OrgSettingsDto.from(organization, privacy.of(organization.getId()), seat.role(),
                members.countByOrganization_Id(organization.getId()));
    }

    private void requireFreeSlug(String slug) {
        if (ReservedHandles.isReserved(slug)) {
            throw new ConflictException("That slug is reserved.", ProblemTypes.HANDLE_RESERVED);
        }
        if (organizations.existsBySlug(slug)) {
            throw new ConflictException("That slug is taken.", ProblemTypes.SLUG_TAKEN);
        }
    }

    private static void requireConfirmation(Organization organization, String confirmSlug) {
        if (!organization.getSlug().equals(confirmSlug)) {
            throw UnprocessableException.on("confirmSlug", "The slug you typed does not match.");
        }
    }
}
