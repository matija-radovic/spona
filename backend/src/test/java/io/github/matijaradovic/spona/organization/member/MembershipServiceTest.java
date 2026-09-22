package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationGate;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.dto.MyOrganizationDto;
import io.github.matijaradovic.spona.organization.dto.OrgMemberDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class MembershipServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f3");
    private static final UUID INVITER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f4");
    private static final Instant JOINED = Instant.parse("2026-01-05T08:00:00Z");
    private static final Instant ASSIGNED = Instant.parse("2026-03-09T12:30:00Z");

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    private final MembershipService service = new MembershipService(new OrganizationGate(organizations, roles),
            members, roles, users);

    private final User marko = TestFixtures.userWithId("marko", CALLER_ID);
    private final User jelena = TestFixtures.userWithId("jelena", MEMBER_ID);
    private final Organization spona = TestFixtures.organization("spona");

    @BeforeEach
    void stubs() {
        ReflectionTestUtils.setField(spona, "id", ORG_ID);
        when(organizations.findBySlug("spona")).thenReturn(Optional.of(spona));
    }

    @Test
    void anyMemberReadsTheRosterInTheOrderItIsReadWithEachLiveRole() {
        holds(OrganizationRole.RECRUITER);
        when(members.findRoster(ORG_ID)).thenReturn(List.of(membership(marko, JOINED), membership(jelena, ASSIGNED)));
        when(roles.findByOrganizationIdAndRevokedAtIsNull(ORG_ID)).thenReturn(List.of(
                TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN),
                TestFixtures.roleAssignment(marko, spona, OrganizationRole.RECRUITER)));

        List<OrgMemberDto> roster = service.roster("spona", marko);

        assertThat(roster)
                .extracting(row -> row.userId(), row -> row.username(), row -> row.role(), row -> row.joinedAt())
                .containsExactly(
                        tuple(CALLER_ID, "marko", OrganizationRole.RECRUITER, JOINED),
                        tuple(MEMBER_ID, "jelena", OrganizationRole.ADMIN, ASSIGNED));
    }

    @Test
    void aMembershipWithNoLiveRoleIsLeftOffTheRoster() {
        holds(OrganizationRole.OWNER);
        when(members.findRoster(ORG_ID)).thenReturn(List.of(membership(marko, JOINED), membership(jelena, JOINED)));
        when(roles.findByOrganizationIdAndRevokedAtIsNull(ORG_ID))
                .thenReturn(List.of(TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER)));

        assertThat(service.roster("spona", marko)).extracting(row -> row.username()).containsExactly("marko");
    }

    @Test
    void aNonMemberIs403OnTheRosterAndNoMembershipIsRead() {
        assertThatThrownBy(() -> service.roster("spona", marko)).isInstanceOf(ForbiddenException.class);

        verify(members, never()).findRoster(any());
    }

    @Test
    void aRoleChangeRevokesTheLiveRowAndFlushesBeforeInsertingTheReplacement() {
        holds(OrganizationRole.OWNER);
        RoleAssignment current = spy(TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN));
        liveRow(MEMBER_ID, current);

        service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR);

        InOrder order = inOrder(current, roles);
        order.verify(current).revoke(any());
        order.verify(roles).flush();
        order.verify(roles).save(any(RoleAssignment.class));
        assertThat(current.getRevokedAt()).isNotNull();
    }

    @Test
    void theReplacementGivesTheSameMemberTheNewRoleGrantedByTheCaller() {
        holds(OrganizationRole.OWNER);
        liveRow(MEMBER_ID, TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN));
        when(users.getReferenceById(CALLER_ID)).thenReturn(marko);

        service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR);

        ArgumentCaptor<RoleAssignment> saved = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roles).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(jelena);
        assertThat(saved.getValue().getOrganization()).isSameAs(spona);
        assertThat(saved.getValue().getRole()).isEqualTo(OrganizationRole.EDITOR);
        assertThat(saved.getValue().getAssignedBy()).isSameAs(marko);
    }

    @Test
    void anAdminMovesAnEditorToAnotherRoleBelowAdmin() {
        holds(OrganizationRole.ADMIN);
        RoleAssignment editor = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.EDITOR);
        liveRow(MEMBER_ID, editor);

        service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.RECRUITER);

        ArgumentCaptor<RoleAssignment> saved = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roles).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(OrganizationRole.RECRUITER);
        assertThat(editor.getRevokedAt()).isNotNull();
    }

    @Test
    void ownerIsNeverAssignedHereAndIs422UnderRoleBeforeAnyRowIsRead() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.OWNER))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("role"));

        verify(roles, never()).findByOrganizationIdAndUserIdAndRevokedAtIsNull(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "PUBLISHER", "EDITOR", "RECRUITER" })
    void aRoleBelowAdminChangesAndRemovesNobodyAndNoTargetIsRead(OrganizationRole role) {
        holds(role);

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.remove("spona", marko, MEMBER_ID)).isInstanceOf(ForbiddenException.class);

        verify(roles, never()).findByOrganizationIdAndUserIdAndRevokedAtIsNull(any(), any());
        verify(roles, never()).findLiveRole(ORG_ID, MEMBER_ID);
        verify(members, never()).deleteById(any());
    }

    @Test
    void aTargetWithNoLiveRoleIs404() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remove("spona", marko, MEMBER_ID)).isInstanceOf(NotFoundException.class);

        verify(roles, never()).save(any());
        verify(members, never()).deleteById(any());
    }

    @Test
    void anAdminAimingAtTheOwnerRowIs409LastOwnerAndWritesNothing() {
        holds(OrganizationRole.ADMIN);
        RoleAssignment owner = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.OWNER);
        liveRow(MEMBER_ID, owner);
        when(roles.findLiveRole(ORG_ID, MEMBER_ID)).thenReturn(Optional.of(OrganizationRole.OWNER));

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAST_OWNER_DEMOTE));
        assertThatThrownBy(() -> service.remove("spona", marko, MEMBER_ID))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAST_OWNER_DEMOTE));

        assertThat(owner.getRevokedAt()).isNull();
        verify(roles, never()).flush();
        verify(members, never()).deleteById(any());
    }

    @Test
    void theOwnerAimingAtTheirOwnRowIs409LastOwner() {
        holds(OrganizationRole.OWNER);
        liveRow(CALLER_ID, TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER));

        assertThatThrownBy(() -> service.changeRole("spona", marko, CALLER_ID, OrganizationRole.ADMIN))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAST_OWNER_DEMOTE));
        assertThatThrownBy(() -> service.remove("spona", marko, CALLER_ID))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAST_OWNER_DEMOTE));

        verify(members, never()).deleteById(any());
    }

    @Test
    void anAdminAimingAtTheirOwnRowIs403() {
        holds(OrganizationRole.ADMIN);
        RoleAssignment own = TestFixtures.roleAssignment(marko, spona, OrganizationRole.ADMIN);
        liveRow(CALLER_ID, own);

        assertThatThrownBy(() -> service.changeRole("spona", marko, CALLER_ID, OrganizationRole.EDITOR))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OWN_MEMBERSHIP));
        assertThatThrownBy(() -> service.remove("spona", marko, CALLER_ID))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OWN_MEMBERSHIP));

        assertThat(own.getRevokedAt()).isNull();
        verify(members, never()).deleteById(any());
    }

    @Test
    void anAdminMayNotChangeOrRemoveAnotherAdmin() {
        holds(OrganizationRole.ADMIN);
        RoleAssignment admin = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN);
        liveRow(MEMBER_ID, admin);
        when(roles.findLiveRole(ORG_ID, MEMBER_ID)).thenReturn(Optional.of(OrganizationRole.ADMIN));

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.remove("spona", marko, MEMBER_ID)).isInstanceOf(ForbiddenException.class);

        assertThat(admin.getRevokedAt()).isNull();
        verify(members, never()).deleteById(any());
    }

    @Test
    void anAdminMayNotGrantAdmin() {
        holds(OrganizationRole.ADMIN);
        RoleAssignment editor = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.EDITOR);
        liveRow(MEMBER_ID, editor);

        assertThatThrownBy(() -> service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.ADMIN))
                .isInstanceOf(ForbiddenException.class);

        assertThat(editor.getRevokedAt()).isNull();
        verify(roles, never()).save(any());
    }

    @Test
    void theRoleAlreadyHeldIsAnsweredWithoutAWrite() {
        holds(OrganizationRole.OWNER);
        RoleAssignment editor = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.EDITOR);
        liveRow(MEMBER_ID, editor);

        service.changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR);

        assertThat(editor.getRevokedAt()).isNull();
        verify(roles, never()).flush();
        verify(roles, never()).save(any());
    }

    @Test
    void aRemovalDeletesTheMembershipByThePairAndWritesNoRoleRow() {
        holds(OrganizationRole.ADMIN);
        when(roles.findLiveRole(ORG_ID, MEMBER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        service.remove("spona", marko, MEMBER_ID);

        verify(members).deleteById(new OrganizationMemberId(MEMBER_ID, ORG_ID));
        verify(roles, never()).findByOrganizationIdAndUserIdAndRevokedAtIsNull(any(), any());
        verify(roles, never()).flush();
        verify(roles, never()).save(any());
    }

    @Test
    void myOrganizationsCarryTheLiveRoleBothDatesTheCountAndTheOrganizationAsARef() {
        stubMembershipOf(OrganizationRole.EDITOR);
        when(members.countByOrganization_Id(ORG_ID)).thenReturn(4L);

        List<MyOrganizationDto> mine = service.organizations(marko);

        assertThat(mine).hasSize(1);
        MyOrganizationDto row = mine.get(0);
        assertThat(row.organization()).isEqualTo(new ActorRefDto(ActorType.ORG, ORG_ID, "Display spona", "spona", null));
        assertThat(row.role()).isEqualTo(OrganizationRole.EDITOR);
        assertThat(row.joinedAt()).isEqualTo(JOINED);
        assertThat(row.roleUpdatedAt()).isEqualTo(ASSIGNED);
        assertThat(row.memberCount()).isEqualTo(4);
    }

    @Test
    void aMembershipWithoutAnAcceptedInvitationHasNoInviter() {
        stubMembershipOf(OrganizationRole.OWNER);

        assertThat(service.organizations(marko).get(0).invitedBy()).isNull();
        verify(users, never()).findById(any());
    }

    @Test
    void theInviterIsTheUserTheAcceptedInvitationNames() {
        stubMembershipOf(OrganizationRole.EDITOR);
        User inviter = TestFixtures.userWithId("ivana", INVITER_ID);
        inviter.setFullName("Ivana Ilić");
        when(members.findInviterId(ORG_ID, CALLER_ID)).thenReturn(Optional.of(INVITER_ID));
        when(users.findById(INVITER_ID)).thenReturn(Optional.of(inviter));

        assertThat(service.organizations(marko).get(0).invitedBy())
                .isEqualTo(new ActorRefDto(ActorType.USER, INVITER_ID, "Ivana Ilić", "ivana", null));
    }

    @Test
    void aMembershipWithNoLiveRoleIsNotListed() {
        when(members.findMemberships(CALLER_ID)).thenReturn(List.of(membership(marko, JOINED)));

        assertThat(service.organizations(marko)).isEmpty();
    }

    @Test
    void aLeaveDeletesTheCallersOwnMembershipAndWritesNoRoleRow() {
        holds(OrganizationRole.EDITOR);

        service.leave("spona", marko);

        verify(members).deleteById(new OrganizationMemberId(CALLER_ID, ORG_ID));
        verify(roles, never()).save(any());
    }

    @Test
    void theOwnerMayNotLeaveAndIs409LastOwner() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.leave("spona", marko))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAST_OWNER_LEAVE));

        verify(members, never()).deleteById(any());
    }

    @Test
    void leavingAnOrganizationTheCallerIsNotInIs404() {
        assertThatThrownBy(() -> service.leave("spona", marko)).isInstanceOf(NotFoundException.class);

        verify(members, never()).deleteById(any());
    }

    @Test
    void leavingASlugThatIsNobodyIs404BeforeAnyRoleIsRead() {
        assertThatThrownBy(() -> service.leave("nepostojeci", marko)).isInstanceOf(NotFoundException.class);

        verify(roles, never()).findLiveRole(any(), any());
    }

    private void holds(OrganizationRole role) {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));
    }

    private void liveRow(UUID userId, RoleAssignment row) {
        when(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(ORG_ID, userId)).thenReturn(Optional.of(row));
    }

    private void stubMembershipOf(OrganizationRole role) {
        RoleAssignment live = TestFixtures.roleAssignment(marko, spona, role);
        ReflectionTestUtils.setField(live, "assignedAt", ASSIGNED);
        when(members.findMemberships(CALLER_ID)).thenReturn(List.of(membership(marko, JOINED)));
        when(roles.findByUserIdAndRevokedAtIsNull(CALLER_ID)).thenReturn(List.of(live));
    }

    private OrganizationMember membership(User user, Instant joinedAt) {
        OrganizationMember membership = TestFixtures.membership(user, spona);
        ReflectionTestUtils.setField(membership, "joinedAt", joinedAt);
        return membership;
    }
}
