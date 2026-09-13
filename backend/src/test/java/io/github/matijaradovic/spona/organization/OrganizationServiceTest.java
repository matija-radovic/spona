package io.github.matijaradovic.spona.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.dto.CreateOrganizationRequest;
import io.github.matijaradovic.spona.organization.dto.OrgSettingsDto;
import io.github.matijaradovic.spona.organization.dto.OrganizationDto;
import io.github.matijaradovic.spona.organization.dto.TransferOwnershipRequest;
import io.github.matijaradovic.spona.organization.dto.UpdateOrganizationRequest;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.storage.AvatarService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class OrganizationServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a7");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b8");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c9");
    private static final String AVATAR = "data:image/png;base64,AAAA";

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PrivacyService privacy = mock(PrivacyService.class);
    private final AvatarService avatars = mock(AvatarService.class);

    private final OrganizationService service = new OrganizationService(organizations, members, roles, users,
            privacy, avatars);

    private final User marko = TestFixtures.userWithId("marko", CALLER_ID);
    private final User jelena = TestFixtures.userWithId("jelena", ADMIN_ID);
    private final Organization spona = TestFixtures.organization("spona");

    @BeforeEach
    void stubs() {
        ReflectionTestUtils.setField(spona, "id", ORG_ID);
        when(users.findById(CALLER_ID)).thenReturn(Optional.of(marko));
        when(organizations.findBySlug("spona")).thenReturn(Optional.of(spona));
        when(privacy.of(ORG_ID)).thenReturn(new PrivacyDto(true));
        when(members.countByOrganization_Id(ORG_ID)).thenReturn(3L);
    }

    @Test
    void aCreateWritesTheOrganizationThenTheMembershipThenTheOwnerRoleForTheStoredCaller() {
        User stored = TestFixtures.userWithId("marko", CALLER_ID);
        when(users.findById(CALLER_ID)).thenReturn(Optional.of(stored));

        OrganizationDto created = service.create(marko, create("nova-firma", null));

        ArgumentCaptor<Organization> organization = ArgumentCaptor.forClass(Organization.class);
        ArgumentCaptor<OrganizationMember> membership = ArgumentCaptor.forClass(OrganizationMember.class);
        ArgumentCaptor<RoleAssignment> role = ArgumentCaptor.forClass(RoleAssignment.class);
        InOrder order = inOrder(organizations, members, roles);
        order.verify(organizations).saveAndFlush(organization.capture());
        order.verify(members).save(membership.capture());
        order.verify(roles).save(role.capture());

        assertThat(membership.getValue().getUser()).isSameAs(stored);
        assertThat(membership.getValue().getOrganization()).isSameAs(organization.getValue());
        assertThat(role.getValue().getRole()).isEqualTo(OrganizationRole.OWNER);
        assertThat(role.getValue().getUser()).isSameAs(stored);
        assertThat(role.getValue().getAssignedBy()).isSameAs(stored);
        assertThat(role.getValue().getOrganization()).isSameAs(organization.getValue());
        assertThat(created.slug()).isEqualTo("nova-firma");
    }

    @Test
    void aCreateNormalisesTheWayTheColumnsRequire() {
        OrganizationDto created = service.create(marko, new CreateOrganizationRequest("nova-firma",
                "  Nova Firma ", " Nova Firma d.o.o. ", "   ", " https://nova.rs ", null, " Beograd ",
                TestFixtures.contacts()));

        assertThat(created.displayName()).isEqualTo("Nova Firma");
        assertThat(created.legalName()).isEqualTo("Nova Firma d.o.o.");
        assertThat(created.description()).isNull();
        assertThat(created.website()).isEqualTo("https://nova.rs");
        assertThat(created.location()).isEqualTo("Beograd");
        assertThat(created.otherContactInfo()).hasSize(2);
    }

    @Test
    void aReservedSlugIsRefusedWithoutALookup() {
        assertThatThrownBy(() -> service.create(marko, create("settings", null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.HANDLE_RESERVED));

        verify(organizations, never()).existsBySlug(anyString());
        verify(organizations, never()).saveAndFlush(any());
    }

    @Test
    void aTakenSlugIsRefusedAndNothingIsWritten() {
        when(organizations.existsBySlug("zauzeto")).thenReturn(true);

        assertThatThrownBy(() -> service.create(marko, create("zauzeto", AVATAR)))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.SLUG_TAKEN));

        verify(avatars, never()).store(any());
        verify(organizations, never()).saveAndFlush(any());
        verify(members, never()).save(any());
        verify(roles, never()).save(any());
    }

    @Test
    void anAvatarIsStoredBeforeTheOrganizationIsWritten() {
        when(avatars.store(AVATAR)).thenReturn("avatar-url");

        OrganizationDto created = service.create(marko, create("nova-firma", AVATAR));

        InOrder order = inOrder(avatars, organizations);
        order.verify(avatars).store(AVATAR);
        order.verify(organizations).saveAndFlush(any());
        assertThat(created.avatarUrl()).isEqualTo("avatar-url");
    }

    @Test
    void aCreateWithoutAnAvatarStoresNothing() {
        OrganizationDto created = service.create(marko, create("nova-firma", null));

        assertThat(created.avatarUrl()).isNull();
        verify(avatars, never()).store(any());
    }

    @Test
    void aCallerWhoseRowIsGoneIsASessionThatIsGone() {
        User ghost = TestFixtures.userWithId("ghost", UUID.randomUUID());

        assertThatThrownBy(() -> service.create(ghost, create("nova-firma", null)))
                .isInstanceOf(UnauthorizedException.class);

        verify(organizations, never()).saveAndFlush(any());
    }

    @Test
    void anyMemberReadsTheSettingsWithTheirOwnRoleTheCountAndPrivacy() {
        holds(OrganizationRole.RECRUITER);

        OrgSettingsDto settings = service.settings("spona", marko);

        assertThat(settings.id()).isEqualTo(ORG_ID);
        assertThat(settings.slug()).isEqualTo("spona");
        assertThat(settings.viewerRole()).isEqualTo(OrganizationRole.RECRUITER);
        assertThat(settings.memberCount()).isEqualTo(3);
        assertThat(settings.privacy().showJobPostings()).isTrue();
    }

    @Test
    void anUnknownSlugIs404BeforeAnyRoleIsRead() {
        assertThatThrownBy(() -> service.settings("nepostojeci", marko)).isInstanceOf(NotFoundException.class);

        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void aCallerWithNoLiveRoleIs403OnTheSettings() {
        assertThatThrownBy(() -> service.settings("spona", marko)).isInstanceOf(ForbiddenException.class);

        verify(privacy, never()).of(any());
    }

    @Test
    void anAdminUpdatesTheProfileAndItIsNormalised() {
        holds(OrganizationRole.ADMIN);

        OrgSettingsDto updated = service.updateProfile("spona", marko, new UpdateOrganizationRequest(
                " Spona ", " Spona d.o.o. ", " Opis ", "   ", null, null, TestFixtures.contacts()));

        assertThat(spona.getDisplayName()).isEqualTo("Spona");
        assertThat(spona.getLegalName()).isEqualTo("Spona d.o.o.");
        assertThat(spona.getDescription()).isEqualTo("Opis");
        assertThat(spona.getWebsite()).isNull();
        assertThat(spona.getLocation()).isNull();
        assertThat(spona.getOtherContactInfo()).hasSize(2);
        assertThat(updated.displayName()).isEqualTo("Spona");
        assertThat(updated.viewerRole()).isEqualTo(OrganizationRole.ADMIN);
    }

    @Test
    void aPublisherMayNotUpdateTheProfile() {
        holds(OrganizationRole.PUBLISHER);

        assertThatThrownBy(() -> service.updateProfile("spona", marko, profile(AVATAR)))
                .isInstanceOf(ForbiddenException.class);

        assertThat(spona.getDisplayName()).isEqualTo("Display spona");
        verify(avatars, never()).replace(any(), any());
    }

    @Test
    void aNewAvatarReplacesTheCurrentOne() {
        holds(OrganizationRole.OWNER);
        spona.setAvatarUrl("old-url");
        when(avatars.replace("old-url", AVATAR)).thenReturn("new-url");

        OrgSettingsDto updated = service.updateProfile("spona", marko, profile(AVATAR));

        assertThat(spona.getAvatarUrl()).isEqualTo("new-url");
        assertThat(updated.avatarUrl()).isEqualTo("new-url");
    }

    @Test
    void aNullAvatarKeepsTheCurrentOne() {
        holds(OrganizationRole.OWNER);
        spona.setAvatarUrl("old-url");

        service.updateProfile("spona", marko, profile(null));

        assertThat(spona.getAvatarUrl()).isEqualTo("old-url");
        verify(avatars, never()).replace(any(), any());
    }

    @Test
    void theSlugAlreadyHeldIsAnsweredWithoutALookup() {
        holds(OrganizationRole.OWNER);

        OrgSettingsDto updated = service.changeSlug("spona", marko, "spona");

        assertThat(updated.slug()).isEqualTo("spona");
        verify(organizations, never()).existsBySlug(anyString());
    }

    @Test
    void aReservedNextSlugIsRefusedWithoutALookup() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.changeSlug("spona", marko, "companies"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.HANDLE_RESERVED));

        assertThat(spona.getSlug()).isEqualTo("spona");
        verify(organizations, never()).existsBySlug(anyString());
    }

    @Test
    void aTakenNextSlugIsRefused() {
        holds(OrganizationRole.OWNER);
        when(organizations.existsBySlug("zauzeto")).thenReturn(true);

        assertThatThrownBy(() -> service.changeSlug("spona", marko, "zauzeto"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.SLUG_TAKEN));

        assertThat(spona.getSlug()).isEqualTo("spona");
    }

    @Test
    void aFreeNextSlugIsWrittenAndAnswered() {
        holds(OrganizationRole.OWNER);

        OrgSettingsDto updated = service.changeSlug("spona", marko, "spona-nova");

        assertThat(spona.getSlug()).isEqualTo("spona-nova");
        assertThat(updated.slug()).isEqualTo("spona-nova");
        assertThat(updated.viewerRole()).isEqualTo(OrganizationRole.OWNER);
    }

    @Test
    void anAdminMayNotChangeTheSlug() {
        holds(OrganizationRole.ADMIN);

        assertThatThrownBy(() -> service.changeSlug("spona", marko, "spona-nova"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(spona.getSlug()).isEqualTo("spona");
    }

    @Test
    void aTransferRevokesBothLiveRowsAndFlushesBeforeInsertingEither() {
        holds(OrganizationRole.OWNER);
        RoleAssignment owner = spy(TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER));
        RoleAssignment admin = spy(TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN));
        liveRow(CALLER_ID, owner);
        liveRow(ADMIN_ID, admin);

        service.transferOwnership("spona", marko, new TransferOwnershipRequest(ADMIN_ID, "spona"));

        InOrder order = inOrder(owner, admin, roles);
        order.verify(owner).revoke(any());
        order.verify(admin).revoke(any());
        order.verify(roles).flush();
        order.verify(roles, times(2)).save(any(RoleAssignment.class));
        assertThat(owner.getRevokedAt()).isNotNull();
        assertThat(admin.getRevokedAt()).isNotNull();
    }

    @Test
    void theReplacementsMakeTheAdminTheOwnerAndTheOwnerAnAdminBothGrantedByTheOwner() {
        holds(OrganizationRole.OWNER);
        liveRow(CALLER_ID, TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER));
        liveRow(ADMIN_ID, TestFixtures.roleAssignment(jelena, spona, OrganizationRole.ADMIN));

        service.transferOwnership("spona", marko, new TransferOwnershipRequest(ADMIN_ID, "spona"));

        ArgumentCaptor<RoleAssignment> saved = ArgumentCaptor.forClass(RoleAssignment.class);
        verify(roles, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(row -> row.getUser(), row -> row.getRole(), row -> row.getAssignedBy(),
                        row -> row.getOrganization())
                .containsExactly(
                        tuple(marko, OrganizationRole.ADMIN, marko, spona),
                        tuple(jelena, OrganizationRole.OWNER, marko, spona));
    }

    @Test
    void aTransferToAMemberWhoIsNotAnAdminIs422AndRevokesNothing() {
        holds(OrganizationRole.OWNER);
        RoleAssignment owner = TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER);
        RoleAssignment editor = TestFixtures.roleAssignment(jelena, spona, OrganizationRole.EDITOR);
        liveRow(CALLER_ID, owner);
        liveRow(ADMIN_ID, editor);

        assertThatThrownBy(() -> service.transferOwnership("spona", marko,
                new TransferOwnershipRequest(ADMIN_ID, "spona")))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("userId"));

        assertThat(owner.getRevokedAt()).isNull();
        assertThat(editor.getRevokedAt()).isNull();
        verify(roles, never()).flush();
        verify(roles, never()).save(any());
    }

    @Test
    void aTransferToTheOwnerThemselvesIs422() {
        holds(OrganizationRole.OWNER);
        RoleAssignment owner = TestFixtures.roleAssignment(marko, spona, OrganizationRole.OWNER);
        liveRow(CALLER_ID, owner);

        assertThatThrownBy(() -> service.transferOwnership("spona", marko,
                new TransferOwnershipRequest(CALLER_ID, "spona")))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("userId"));

        assertThat(owner.getRevokedAt()).isNull();
    }

    @Test
    void aTransferToSomebodyOutsideTheOrganizationIs422() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.transferOwnership("spona", marko,
                new TransferOwnershipRequest(UUID.randomUUID(), "spona")))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("userId"));

        verify(roles, never()).save(any());
    }

    @Test
    void aMismatchedConfirmationIs422BeforeAnyRowIsRead() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.transferOwnership("spona", marko,
                new TransferOwnershipRequest(ADMIN_ID, "Spona")))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("confirmSlug"));

        verify(roles, never()).findByOrganizationIdAndUserIdAndRevokedAtIsNull(any(), any());
    }

    @Test
    void anAdminMayNotTransferOwnership() {
        holds(OrganizationRole.ADMIN);

        assertThatThrownBy(() -> service.transferOwnership("spona", marko,
                new TransferOwnershipRequest(ADMIN_ID, "spona")))
                .isInstanceOf(ForbiddenException.class);

        verify(roles, never()).findByOrganizationIdAndUserIdAndRevokedAtIsNull(any(), any());
    }

    @Test
    void theOwnerDeletesTheOrganizationAndDiscardsItsAvatar() {
        holds(OrganizationRole.OWNER);
        spona.setAvatarUrl("org-avatar");

        service.delete("spona", marko, "spona");

        verify(avatars).discard("org-avatar");
        verify(organizations).delete(spona);
    }

    @Test
    void aMismatchedConfirmationDeletesNothing() {
        holds(OrganizationRole.OWNER);

        assertThatThrownBy(() -> service.delete("spona", marko, "nije-spona"))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("confirmSlug"));

        verify(organizations, never()).delete(any());
        verify(avatars, never()).discard(any());
    }

    @Test
    void anAdminMayNotDelete() {
        holds(OrganizationRole.ADMIN);

        assertThatThrownBy(() -> service.delete("spona", marko, "spona")).isInstanceOf(ForbiddenException.class);

        verify(organizations, never()).delete(any());
    }

    private void holds(OrganizationRole role) {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));
    }

    private void liveRow(UUID userId, RoleAssignment row) {
        when(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(ORG_ID, userId)).thenReturn(Optional.of(row));
    }

    private static CreateOrganizationRequest create(String slug, String avatar) {
        return new CreateOrganizationRequest(slug, "Nova Firma", "Nova Firma d.o.o.", null, null, avatar, null,
                List.of());
    }

    private static UpdateOrganizationRequest profile(String avatar) {
        return new UpdateOrganizationRequest("Spona", "Spona d.o.o.", null, null, avatar, null, List.of());
    }
}
