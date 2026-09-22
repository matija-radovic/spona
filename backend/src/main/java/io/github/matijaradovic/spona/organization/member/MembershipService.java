package io.github.matijaradovic.spona.organization.member;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.OrganizationGate;
import io.github.matijaradovic.spona.organization.OrganizationGate.Seat;
import io.github.matijaradovic.spona.organization.dto.MyOrganizationDto;
import io.github.matijaradovic.spona.organization.dto.OrgMemberDto;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class MembershipService {

    private final OrganizationGate gate;
    private final OrganizationMemberRepository members;
    private final RoleAssignmentRepository roles;
    private final UserRepository users;

    public MembershipService(OrganizationGate gate, OrganizationMemberRepository members,
            RoleAssignmentRepository roles, UserRepository users) {
        this.gate = gate;
        this.members = members;
        this.roles = roles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<OrgMemberDto> roster(String slug, User caller) {
        UUID organizationId = gate.seat(slug, caller, PublishAuthority::canReadSettings,
                ErrorCode.ORGANIZATION_NOT_A_MEMBER).organization().getId();
        Map<UUID, OrganizationRole> live = roles.findByOrganizationIdAndRevokedAtIsNull(organizationId).stream()
                .collect(Collectors.toMap(row -> row.getUser().getId(), row -> row.getRole()));
        return members.findRoster(organizationId).stream()
                .filter(membership -> live.containsKey(membership.getUser().getId()))
                .map(membership -> OrgMemberDto.from(membership, live.get(membership.getUser().getId())))
                .toList();
    }

    @Transactional
    public void changeRole(String slug, User caller, UUID userId, OrganizationRole role) {
        Seat seat = gate.seat(slug, caller, PublishAuthority::canManageMembers, ErrorCode.MEMBER_NOT_AN_ADMIN);
        if (role == OrganizationRole.OWNER) {
            throw UnprocessableException.on("role", ErrorCode.MEMBER_OWNER_BY_TRANSFER_ONLY);
        }
        RoleAssignment current = roles
                .findByOrganizationIdAndUserIdAndRevokedAtIsNull(seat.organization().getId(), userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));
        requireReach(seat, caller, userId, current.getRole());
        if (!PublishAuthority.canManageRole(seat.role(), role)) {
            throw new ForbiddenException(ErrorCode.ROLE_NOT_ASSIGNABLE);
        }
        if (current.getRole() == role) {
            return;
        }

        current.revoke(Instant.now());
        roles.flush();
        roles.save(new RoleAssignment(current.getUser(), seat.organization(), role,
                users.getReferenceById(caller.getId())));
    }

    @Transactional
    public void remove(String slug, User caller, UUID userId) {
        Seat seat = gate.seat(slug, caller, PublishAuthority::canManageMembers, ErrorCode.MEMBER_NOT_AN_ADMIN);
        UUID organizationId = seat.organization().getId();
        OrganizationRole held = roles.findLiveRole(organizationId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));
        requireReach(seat, caller, userId, held);
        members.deleteById(new OrganizationMemberId(userId, organizationId));
    }

    @Transactional(readOnly = true)
    public List<MyOrganizationDto> organizations(User caller) {
        Map<UUID, RoleAssignment> live = roles.findByUserIdAndRevokedAtIsNull(caller.getId()).stream()
                .collect(Collectors.toMap(row -> row.getOrganization().getId(), row -> row));
        return members.findMemberships(caller.getId()).stream()
                .filter(membership -> live.containsKey(membership.getOrganization().getId()))
                .map(membership -> {
                    UUID organizationId = membership.getOrganization().getId();
                    return MyOrganizationDto.from(membership, live.get(organizationId),
                            inviter(organizationId, caller.getId()), members.countByOrganization_Id(organizationId));
                })
                .toList();
    }

    @Transactional
    public void leave(String slug, User caller) {
        Seat seat = gate.seat(slug, caller);
        if (seat.role() == null) {
            throw new NotFoundException(ErrorCode.MEMBER_NOT_A_MEMBER);
        }
        if (seat.role() == OrganizationRole.OWNER) {
            throw new ConflictException(ErrorCode.LAST_OWNER_LEAVE);
        }
        members.deleteById(new OrganizationMemberId(caller.getId(), seat.organization().getId()));
    }

    private static void requireReach(Seat seat, User caller, UUID userId, OrganizationRole held) {
        if (held == OrganizationRole.OWNER) {
            throw new ConflictException(ErrorCode.LAST_OWNER_DEMOTE);
        }
        if (userId.equals(caller.getId())) {
            throw new ForbiddenException(ErrorCode.OWN_MEMBERSHIP);
        }
        if (!PublishAuthority.canManageRole(seat.role(), held)) {
            throw new ForbiddenException(ErrorCode.ROLE_OUT_OF_REACH);
        }
    }

    private ActorRefDto inviter(UUID organizationId, UUID userId) {
        return members.findInviterId(organizationId, userId)
                .flatMap(users::findById)
                .map(ActorRefDto::from)
                .orElse(null);
    }
}
