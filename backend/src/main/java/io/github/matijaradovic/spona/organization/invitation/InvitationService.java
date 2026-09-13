package io.github.matijaradovic.spona.organization.invitation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.common.util.TransactionHooks;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationGate;
import io.github.matijaradovic.spona.organization.OrganizationGate.Seat;
import io.github.matijaradovic.spona.organization.dto.InvitationDto;
import io.github.matijaradovic.spona.organization.dto.MyInvitationDto;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberId;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class InvitationService {

    private static final String NOT_AN_ADMIN = "Only the owner or an admin may manage invitations.";
    private static final String NO_SUCH_INVITATION = "Invitation not found.";

    private static final List<InvitationStatus> LISTED = List.of(InvitationStatus.WAITING, InvitationStatus.DECLINED);

    private final OrganizationGate gate;
    private final OrganizationInvitationRepository invitations;
    private final OrganizationMemberRepository members;
    private final RoleAssignmentRepository roles;
    private final UserRepository users;
    private final InvitationCanceler canceler;
    private final InvitationMailer mailer;

    public InvitationService(OrganizationGate gate, OrganizationInvitationRepository invitations,
            OrganizationMemberRepository members, RoleAssignmentRepository roles, UserRepository users,
            InvitationCanceler canceler, InvitationMailer mailer) {
        this.gate = gate;
        this.invitations = invitations;
        this.members = members;
        this.roles = roles;
        this.users = users;
        this.canceler = canceler;
        this.mailer = mailer;
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> list(String slug, User caller) {
        UUID organizationId = gate.seat(slug, caller, PublishAuthority::canManageInvitations, NOT_AN_ADMIN)
                .organization().getId();
        return invitations.findListed(organizationId, LISTED).stream().map(InvitationDto::from).toList();
    }

    /** An address is never looked up here - the mail it gets is chosen after the commit, off this thread */
    @Transactional
    public InvitationDto create(String slug, User caller, String identifier, OrganizationRole role) {
        Seat seat = gate.seat(slug, caller, PublishAuthority::canManageInvitations, NOT_AN_ADMIN);
        if (role == OrganizationRole.OWNER) {
            throw UnprocessableException.on("role", "Ownership moves only by transfer.");
        }
        if (!PublishAuthority.canManageRole(seat.role(), role)) {
            throw new ForbiddenException("You may not invite someone at this role.");
        }

        Organization organization = seat.organization();
        InvitationMailer.Offer offer = new InvitationMailer.Offer(organization.resolveDisplayName(),
                caller.resolveDisplayName(), caller.getUsername(), role);
        String typed = identifier.trim();
        if (typed.contains("@")) {
            String email = Strings.normalizeEmail(typed);
            OrganizationInvitation invitation = save(organization, role, null, email, caller);
            TransactionHooks.afterCommit(() -> mailer.toAddress(email, offer));
            return InvitationDto.from(invitation);
        }

        User invitee = users.findByUsername(typed.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> UnprocessableException.on("identifier", "No user has this username."));
        if (members.existsById(new OrganizationMemberId(invitee.getId(), organization.getId()))) {
            throw new ConflictException("This user is already a member.", ProblemTypes.ALREADY_MEMBER);
        }
        if (invitations.existsByOrganization_IdAndInvitee_IdAndStatus(organization.getId(), invitee.getId(),
                InvitationStatus.WAITING)) {
            throw new ConflictException("This user already has a waiting invitation.");
        }
        String email = invitee.getEmail();
        String displayName = invitee.resolveDisplayName();
        OrganizationInvitation invitation = save(organization, role, invitee, email, caller);
        TransactionHooks.afterCommit(() -> mailer.toUser(email, displayName, offer));
        return InvitationDto.from(invitation);
    }

    @Transactional
    public void cancel(String slug, User caller, UUID id) {
        OrganizationInvitation invitation = ofOrganization(slug, caller, id);
        if (invitation.getStatus() != InvitationStatus.WAITING) {
            throw new ConflictException("Only a waiting invitation can be canceled.");
        }
        invitation.cancel(Instant.now());
    }

    @Transactional
    public void dismiss(String slug, User caller, UUID id) {
        OrganizationInvitation invitation = ofOrganization(slug, caller, id);
        if (invitation.getStatus() != InvitationStatus.DECLINED) {
            throw new ConflictException("Only a declined invitation can be dismissed.");
        }
        invitation.dismiss(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<MyInvitationDto> mine(User caller) {
        return invitations.findAddressedTo(caller.getId(), caller.getEmail(), InvitationStatus.WAITING).stream()
                .map(MyInvitationDto::from)
                .toList();
    }

    @Transactional
    public void accept(UUID id, User caller) {
        OrganizationInvitation invitation = waitingFor(id, caller);
        Organization organization = invitation.getOrganization();
        if (members.existsById(new OrganizationMemberId(caller.getId(), organization.getId()))) {
            canceler.cancel(invitation.getId());
            throw new ConflictException("You are already a member of this organization.",
                    ProblemTypes.ALREADY_MEMBER);
        }

        User user = users.getReferenceById(caller.getId());
        invitation.accept(user, Instant.now());
        members.save(new OrganizationMember(user, organization));
        roles.save(new RoleAssignment(user, organization, invitation.getRole(), invitation.getInvitedBy()));
    }

    @Transactional
    public void decline(UUID id, User caller) {
        waitingFor(id, caller).decline(Instant.now());
    }

    /**
     * Gives every unbound waiting row sent to the user's address its invitee. Where the user already holds a
     * waiting row from the same organization the older one stays and the other is canceled, and the cancels
     * are flushed first so `invitation_waiting_user_key` never sees both.
     */
    @Transactional
    public void bind(User user) {
        List<OrganizationInvitation> unbound = invitations.findUnbound(user.getEmail(), InvitationStatus.WAITING);
        if (unbound.isEmpty()) {
            return;
        }
        Map<UUID, OrganizationInvitation> held = invitations.findByInvitee_IdAndStatus(user.getId(),
                InvitationStatus.WAITING).stream()
                .collect(Collectors.toMap(row -> row.getOrganization().getId(), row -> row));

        Instant now = Instant.now();
        List<OrganizationInvitation> binding = new ArrayList<>();
        for (OrganizationInvitation row : unbound) {
            OrganizationInvitation twin = held.get(row.getOrganization().getId());
            if (twin == null) {
                binding.add(row);
            } else if (twin.getCreatedAt().isAfter(row.getCreatedAt())) {
                twin.cancel(now);
                binding.add(row);
            } else {
                row.cancel(now);
            }
        }
        invitations.flush();
        binding.forEach(row -> row.bind(user));
    }

    private OrganizationInvitation save(Organization organization, OrganizationRole role, User invitee,
            String email, User caller) {
        OrganizationInvitation invitation = new OrganizationInvitation(organization, role, invitee, email,
                users.getReferenceById(caller.getId()));
        invitations.saveAndFlush(invitation);
        return invitation;
    }

    private OrganizationInvitation ofOrganization(String slug, User caller, UUID id) {
        UUID organizationId = gate.seat(slug, caller, PublishAuthority::canManageInvitations, NOT_AN_ADMIN)
                .organization().getId();
        return invitations.findByIdAndOrganization_Id(id, organizationId)
                .orElseThrow(() -> new NotFoundException(NO_SUCH_INVITATION));
    }

    private OrganizationInvitation waitingFor(UUID id, User caller) {
        OrganizationInvitation invitation = invitations.findById(id)
                .orElseThrow(() -> new NotFoundException(NO_SUCH_INVITATION));
        if (!invitation.isAddressedTo(caller)) {
            throw new ForbiddenException("This invitation is addressed to someone else.",
                    ProblemTypes.INVITATION_NOT_YOURS);
        }
        if (invitation.getStatus() != InvitationStatus.WAITING) {
            throw new ConflictException("This invitation is no longer waiting.");
        }
        return invitation;
    }
}
