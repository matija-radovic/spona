package io.github.matijaradovic.spona.organization.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationGate;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.dto.InvitationDto;
import io.github.matijaradovic.spona.organization.dto.MyInvitationDto;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberId;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class InvitationServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b1");
    private static final UUID INVITEE_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b3");
    private static final UUID OTHER_ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b4");
    private static final UUID INVITATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b5");
    private static final UUID TWIN_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b6");
    private static final Instant EARLY = Instant.parse("2026-02-01T09:00:00Z");
    private static final Instant LATE = Instant.parse("2026-02-08T09:00:00Z");

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final OrganizationInvitationRepository invitations = mock(OrganizationInvitationRepository.class);
    private final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InvitationCanceler canceler = mock(InvitationCanceler.class);
    private final InvitationMailer mailer = mock(InvitationMailer.class);

    private final InvitationService service = new InvitationService(new OrganizationGate(organizations, roles),
            invitations, members, roles, users, canceler, mailer);

    private final User marko = TestFixtures.userWithId("marko", CALLER_ID);
    private final User jelena = TestFixtures.userWithId("jelena", INVITEE_ID);
    private final Organization spona = TestFixtures.organization("spona");
    private final Organization other = TestFixtures.organization("druga");

    @BeforeEach
    void stubs() {
        ReflectionTestUtils.setField(spona, "id", ORG_ID);
        ReflectionTestUtils.setField(other, "id", OTHER_ORG_ID);
        when(organizations.findBySlug("spona")).thenReturn(Optional.of(spona));
    }

    @Test
    void theListServesEachRowOnItsOwnArmInTheOrderItIsRead() {
        holds(OrganizationRole.ADMIN);
        OrganizationInvitation byAddress = row(TestFixtures.addressInvitation(spona, "nova@primer.rs", marko,
                OrganizationRole.RECRUITER), INVITATION_ID, LATE);
        OrganizationInvitation byUsername = row(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR), TWIN_ID, EARLY);
        when(invitations.findListed(ORG_ID, List.of(InvitationStatus.WAITING, InvitationStatus.DECLINED)))
                .thenReturn(List.of(byAddress, byUsername));

        List<InvitationDto> listed = service.list("spona", marko);

        assertThat(listed).extracting(row -> row.id()).containsExactly(INVITATION_ID, TWIN_ID);
        assertThat(listed.get(0).invitee()).isNull();
        assertThat(listed.get(0).email()).isEqualTo("nova@primer.rs");
        assertThat(listed.get(1).invitee().username()).isEqualTo("jelena");
        assertThat(listed.get(1).email()).isNull();
        assertThat(listed).extracting(row -> row.invitedByUsername()).containsOnly("marko");
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "PUBLISHER", "EDITOR", "RECRUITER" })
    void aRoleBelowAdminIs403OnEveryOrganizationRouteAndNoInvitationIsRead(OrganizationRole role) {
        holds(role);

        assertThatThrownBy(() -> service.list("spona", marko)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create("spona", marko, "nova@primer.rs", OrganizationRole.EDITOR))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.cancel("spona", marko, INVITATION_ID)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.dismiss("spona", marko, INVITATION_ID)).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(invitations, users, mailer);
    }

    @Test
    void aNonMemberIs403OnTheList() {
        assertThatThrownBy(() -> service.list("spona", marko)).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(invitations);
    }

    @Test
    void anAddressIsSavedUnboundAndLowercasedAndNothingIsLookedUp() {
        holds(OrganizationRole.OWNER);
        when(users.getReferenceById(CALLER_ID)).thenReturn(marko);

        InvitationDto created = service.create("spona", marko, "  Nova.Koleginica@Primer.RS ",
                OrganizationRole.RECRUITER);

        OrganizationInvitation saved = saved();
        assertThat(saved.getInvitee()).isNull();
        assertThat(saved.getInviteeEmail()).isEqualTo("nova.koleginica@primer.rs");
        assertThat(saved.getRole()).isEqualTo(OrganizationRole.RECRUITER);
        assertThat(saved.getStatus()).isEqualTo(InvitationStatus.WAITING);
        assertThat(saved.getInvitedBy()).isSameAs(marko);
        assertThat(created.invitee()).isNull();
        assertThat(created.email()).isEqualTo("nova.koleginica@primer.rs");
        assertThat(created.invitedByUsername()).isEqualTo("marko");

        verify(users, only()).getReferenceById(CALLER_ID);
        verifyNoInteractions(members);
        verify(mailer).toAddress("nova.koleginica@primer.rs", offer(OrganizationRole.RECRUITER));
        verify(mailer, never()).toUser(any(), any(), any());
    }

    @Test
    void aUsernameIsSavedBoundWithASnapshotOfTheAddressAndMailedToThatAddress() {
        holds(OrganizationRole.ADMIN);
        when(users.findByUsername("jelena")).thenReturn(Optional.of(jelena));

        InvitationDto created = service.create("spona", marko, " Jelena ", OrganizationRole.EDITOR);

        OrganizationInvitation saved = saved();
        assertThat(saved.getInvitee()).isSameAs(jelena);
        assertThat(saved.getInviteeEmail()).isEqualTo("jelena@example.com");
        assertThat(created.invitee().username()).isEqualTo("jelena");
        assertThat(created.email()).isNull();

        verify(mailer).toUser("jelena@example.com", "jelena", offer(OrganizationRole.EDITOR));
        verify(mailer, never()).toAddress(any(), any());
    }

    @Test
    void aUsernameNobodyHoldsIs422UnderIdentifierAndNothingIsSaved() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.create("spona", marko, "nepostojeci", OrganizationRole.EDITOR))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("identifier"));

        verify(invitations, never()).saveAndFlush(any());
        verifyNoInteractions(mailer);
    }

    @Test
    void aUsernameAlreadyInTheOrganizationIs409AlreadyMember() {
        holds(OrganizationRole.OWNER);
        when(users.findByUsername("jelena")).thenReturn(Optional.of(jelena));
        when(members.existsById(new OrganizationMemberId(INVITEE_ID, ORG_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.create("spona", marko, "jelena", OrganizationRole.EDITOR))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_MEMBER));

        verify(invitations, never()).saveAndFlush(any());
        verifyNoInteractions(mailer);
    }

    @Test
    void aUsernameWithAWaitingInvitationHereIs409AndNothingIsSaved() {
        holds(OrganizationRole.OWNER);
        when(users.findByUsername("jelena")).thenReturn(Optional.of(jelena));
        when(invitations.existsByOrganization_IdAndInvitee_IdAndStatus(ORG_ID, INVITEE_ID, InvitationStatus.WAITING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create("spona", marko, "jelena", OrganizationRole.EDITOR))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITATION_ALREADY_WAITING));

        verify(invitations, never()).saveAndFlush(any());
    }

    @Test
    void ownerIsNeverOfferedAndIs422UnderRoleBeforeTheIdentifierIsRead() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.create("spona", marko, "jelena", OrganizationRole.OWNER))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("role"));

        verifyNoInteractions(users, invitations, mailer);
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER, ADMIN,     true",
            "OWNER, RECRUITER, true",
            "ADMIN, ADMIN,     false",
            "ADMIN, PUBLISHER, true",
            "ADMIN, EDITOR,    true",
            "ADMIN, RECRUITER, true"
    })
    void theOfferedRoleIsOneCanManageRoleAllows(OrganizationRole caller, OrganizationRole offered, boolean allowed) {
        holds(caller);

        if (allowed) {
            service.create("spona", marko, "nova@primer.rs", offered);
            assertThat(saved().getRole()).isEqualTo(offered);
        } else {
            assertThatThrownBy(() -> service.create("spona", marko, "nova@primer.rs", offered))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(invitations, mailer);
        }
    }

    @Test
    void cancelMovesAWaitingRowToCanceledWithItsResolvedAt() {
        holds(OrganizationRole.ADMIN);
        OrganizationInvitation waiting = listed(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR));

        service.cancel("spona", marko, INVITATION_ID);

        assertThat(waiting.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(waiting.getResolvedAt()).isNotNull();
    }

    @Test
    void cancelOnARowThatIsNoLongerWaitingIs409AndChangesNothing() {
        holds(OrganizationRole.OWNER);
        OrganizationInvitation declined = listed(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR));
        declined.decline(EARLY);

        assertThatThrownBy(() -> service.cancel("spona", marko, INVITATION_ID)).isInstanceOf(ConflictException.class);

        assertThat(declined.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(declined.getResolvedAt()).isEqualTo(EARLY);
    }

    @Test
    void dismissStampsADeclinedRowAndLeavesItDeclined() {
        holds(OrganizationRole.ADMIN);
        OrganizationInvitation declined = listed(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR));
        declined.decline(EARLY);

        service.dismiss("spona", marko, INVITATION_ID);

        assertThat(declined.getDismissedAt()).isNotNull();
        assertThat(declined.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(declined.getResolvedAt()).isEqualTo(EARLY);
    }

    @Test
    void dismissOnARowThatIsNotDeclinedIs409() {
        holds(OrganizationRole.OWNER);
        OrganizationInvitation waiting = listed(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR));

        assertThatThrownBy(() -> service.dismiss("spona", marko, INVITATION_ID))
                .isInstanceOf(ConflictException.class);

        assertThat(waiting.getDismissedAt()).isNull();
    }

    @Test
    void anIdThatIsNotThisOrganizationsIs404OnCancelAndDismiss() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.cancel("spona", marko, INVITATION_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.dismiss("spona", marko, INVITATION_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void myInvitationsAreReadByMyIdAndMyVerifiedAddress() {
        OrganizationInvitation fromMarko = row(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR), INVITATION_ID, LATE);
        OrganizationInvitation orphaned = row(TestFixtures.addressInvitation(other, "jelena@example.com", null,
                OrganizationRole.RECRUITER), TWIN_ID, EARLY);
        when(invitations.findAddressedTo(INVITEE_ID, "jelena@example.com", InvitationStatus.WAITING))
                .thenReturn(List.of(fromMarko, orphaned));

        List<MyInvitationDto> mine = service.mine(jelena);

        assertThat(mine).extracting(row -> row.id()).containsExactly(INVITATION_ID, TWIN_ID);
        assertThat(mine.get(0).organization().handle()).isEqualTo("spona");
        assertThat(mine.get(0).invitedBy().handle()).isEqualTo("marko");
        assertThat(mine.get(0).role()).isEqualTo(OrganizationRole.EDITOR);
        assertThat(mine.get(0).createdAt()).isEqualTo(LATE);
        assertThat(mine.get(1).organization().handle()).isEqualTo("druga");
        assertThat(mine.get(1).invitedBy()).isNull();
    }

    @Test
    void acceptWritesTheRowTheMembershipAndARoleGrantedByTheInviter() {
        OrganizationInvitation invitation = found(TestFixtures.invitation(spona, marko, jelena,
                OrganizationRole.RECRUITER));
        when(users.getReferenceById(CALLER_ID)).thenReturn(marko);

        service.accept(INVITATION_ID, marko);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getResolvedAt()).isNotNull();
        assertThat(invitation.getInvitedBy()).isSameAs(jelena);

        ArgumentCaptor<OrganizationMember> membership = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(members).save(membership.capture());
        assertThat(membership.getValue().getUser()).isSameAs(marko);
        assertThat(membership.getValue().getOrganization()).isSameAs(spona);

        RoleAssignment grant = granted();
        assertThat(grant.getUser()).isSameAs(marko);
        assertThat(grant.getOrganization()).isSameAs(spona);
        assertThat(grant.getRole()).isEqualTo(OrganizationRole.RECRUITER);
        assertThat(grant.getAssignedBy()).isSameAs(jelena);
    }

    @Test
    void acceptingAnUnboundRowSentToTheCallersAddressBindsItInTheSameWrite() {
        OrganizationInvitation invitation = found(TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.EDITOR));
        when(users.getReferenceById(CALLER_ID)).thenReturn(marko);

        service.accept(INVITATION_ID, marko);

        assertThat(invitation.getInvitee()).isSameAs(marko);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void anInviterWhoseAccountIsGoneLeavesTheGrantWithNoAssigner() {
        found(TestFixtures.invitation(spona, marko, null, OrganizationRole.EDITOR));
        when(users.getReferenceById(CALLER_ID)).thenReturn(marko);

        service.accept(INVITATION_ID, marko);

        assertThat(granted().getAssignedBy()).isNull();
    }

    @Test
    void acceptingAsAMemberCancelsThroughTheCancelerAndIs409AlreadyMember() {
        OrganizationInvitation invitation = found(TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.EDITOR));
        when(members.existsById(new OrganizationMemberId(CALLER_ID, ORG_ID))).thenReturn(true);

        assertThatThrownBy(() -> service.accept(INVITATION_ID, marko))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_MEMBER_SELF));

        verify(canceler).cancel(INVITATION_ID);
        verify(members, never()).save(any());
        verify(roles, never()).save(any());
        assertThat(invitation.getInvitee()).isNull();
    }

    @Test
    void anIdThatIsNobodyIs404OnAcceptAndDecline() {
        assertThatThrownBy(() -> service.accept(INVITATION_ID, marko)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.decline(INVITATION_ID, marko)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aRowBoundToSomeoneElseIs403NotYoursWhateverItsStatus() {
        OrganizationInvitation declined = found(TestFixtures.invitation(spona, jelena, marko,
                OrganizationRole.EDITOR));
        declined.decline(EARLY);

        assertThatThrownBy(() -> service.accept(INVITATION_ID, marko)).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITATION_NOT_YOURS));
        assertThatThrownBy(() -> service.decline(INVITATION_ID, marko)).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITATION_NOT_YOURS));

        verifyNoInteractions(members, canceler);
    }

    @Test
    void anUnboundRowSentToAnotherAddressIs403NotYours() {
        found(TestFixtures.addressInvitation(spona, "neko.drugi@primer.rs", jelena, OrganizationRole.EDITOR));

        assertThatThrownBy(() -> service.accept(INVITATION_ID, marko)).isInstanceOfSatisfying(
                ForbiddenException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVITATION_NOT_YOURS));
    }

    @Test
    void aRowOfTheCallersThatIsNoLongerWaitingIs409OnAcceptAndDecline() {
        OrganizationInvitation canceled = found(TestFixtures.invitation(spona, marko, jelena,
                OrganizationRole.EDITOR));
        canceled.cancel(EARLY);

        assertThatThrownBy(() -> service.accept(INVITATION_ID, marko)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decline(INVITATION_ID, marko)).isInstanceOf(ConflictException.class);

        assertThat(canceled.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        verifyNoInteractions(members, canceler);
    }

    @Test
    void declineStampsTheRowAndLeavesAnUnboundRowUnbound() {
        OrganizationInvitation invitation = found(TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.EDITOR));

        service.decline(INVITATION_ID, marko);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(invitation.getResolvedAt()).isNotNull();
        assertThat(invitation.getInvitee()).isNull();
    }

    @Test
    void bindingGivesEveryUnboundRowSentToTheAddressItsInvitee() {
        OrganizationInvitation here = TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.EDITOR);
        OrganizationInvitation there = TestFixtures.addressInvitation(other, "marko@example.com", jelena,
                OrganizationRole.RECRUITER);
        when(invitations.findUnbound("marko@example.com", InvitationStatus.WAITING)).thenReturn(List.of(here, there));

        service.bind(marko);

        assertThat(here.getInvitee()).isSameAs(marko);
        assertThat(there.getInvitee()).isSameAs(marko);
        assertThat(List.of(here, there)).extracting(row -> row.getStatus()).containsOnly(InvitationStatus.WAITING);
    }

    @Test
    void anOlderBoundRowFromTheSameOrganizationStaysAndTheUnboundOneIsCanceled() {
        OrganizationInvitation held = row(TestFixtures.invitation(spona, marko, jelena, OrganizationRole.EDITOR),
                TWIN_ID, EARLY);
        OrganizationInvitation unbound = row(TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.ADMIN), INVITATION_ID, LATE);
        when(invitations.findUnbound("marko@example.com", InvitationStatus.WAITING)).thenReturn(List.of(unbound));
        when(invitations.findByInvitee_IdAndStatus(CALLER_ID, InvitationStatus.WAITING)).thenReturn(List.of(held));

        service.bind(marko);

        assertThat(unbound.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(unbound.getResolvedAt()).isNotNull();
        assertThat(unbound.getInvitee()).isNull();
        assertThat(held.getStatus()).isEqualTo(InvitationStatus.WAITING);
    }

    @Test
    void aNewerBoundTwinIsCanceledAndFlushedBeforeTheOlderRowIsBound() {
        OrganizationInvitation twin = spy(row(TestFixtures.invitation(spona, marko, jelena,
                OrganizationRole.EDITOR), TWIN_ID, LATE));
        OrganizationInvitation unbound = spy(row(TestFixtures.addressInvitation(spona, "marko@example.com", jelena,
                OrganizationRole.ADMIN), INVITATION_ID, EARLY));
        when(invitations.findUnbound("marko@example.com", InvitationStatus.WAITING)).thenReturn(List.of(unbound));
        when(invitations.findByInvitee_IdAndStatus(CALLER_ID, InvitationStatus.WAITING)).thenReturn(List.of(twin));

        service.bind(marko);

        InOrder order = inOrder(twin, invitations, unbound);
        order.verify(twin).cancel(any());
        order.verify(invitations).flush();
        order.verify(unbound).bind(marko);
        assertThat(twin.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(unbound.getStatus()).isEqualTo(InvitationStatus.WAITING);
    }

    @Test
    void nothingUnboundForTheAddressReadsNoTwinAndFlushesNothing() {
        service.bind(marko);

        verify(invitations).findUnbound("marko@example.com", InvitationStatus.WAITING);
        verify(invitations, never()).findByInvitee_IdAndStatus(any(), any());
        verify(invitations, never()).flush();
    }

    private void holds(OrganizationRole role) {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));
    }

    private static OrganizationInvitation row(OrganizationInvitation invitation, UUID id, Instant createdAt) {
        ReflectionTestUtils.setField(invitation, "id", id);
        ReflectionTestUtils.setField(invitation, "createdAt", createdAt);
        return invitation;
    }

    private OrganizationInvitation found(OrganizationInvitation invitation) {
        row(invitation, INVITATION_ID, EARLY);
        when(invitations.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
        return invitation;
    }

    private OrganizationInvitation listed(OrganizationInvitation invitation) {
        row(invitation, INVITATION_ID, EARLY);
        when(invitations.findByIdAndOrganization_Id(INVITATION_ID, ORG_ID)).thenReturn(Optional.of(invitation));
        return invitation;
    }

    private OrganizationInvitation saved() {
        ArgumentCaptor<OrganizationInvitation> saved = ArgumentCaptor.forClass(OrganizationInvitation.class);
        verify(invitations).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private RoleAssignment granted() {
        ArgumentCaptor<RoleAssignment> grant = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roles).save(grant.capture());
        return grant.getValue();
    }

    private static InvitationMailer.Offer offer(OrganizationRole role) {
        return new InvitationMailer.Offer("Display spona", "marko", "marko", role);
    }
}
