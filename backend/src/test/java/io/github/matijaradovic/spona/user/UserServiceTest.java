package io.github.matijaradovic.spona.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.auth.VerificationService;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.PasswordResetToken;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.storage.AvatarService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.dto.ChangeEmailRequest;
import io.github.matijaradovic.spona.user.dto.ChangePasswordRequest;
import io.github.matijaradovic.spona.user.dto.UpdateProfileRequest;
import io.github.matijaradovic.spona.user.dto.UserSettingsDto;

class UserServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final String PASSWORD = "right-password";

    private final UserRepository users = mock(UserRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final VerificationService verification = mock(VerificationService.class);
    private final PrivacyService privacy = mock(PrivacyService.class);
    private final AvatarService avatars = mock(AvatarService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);

    private final UserService service = new UserService(users, organizations, roles, resetTokens,
            verification, privacy, avatars, passwords);

    private final User marko = TestFixtures.userWithId("marko", ID);

    @BeforeEach
    void stubs() {
        when(users.findById(ID)).thenReturn(Optional.of(marko));
        when(privacy.of(ID)).thenReturn(new PrivacyDto(true));
        when(passwords.matches(PASSWORD, marko.getPasswordDigest())).thenReturn(true);
    }

    @Test
    void aFreeHandleIsAvailable() {
        when(users.existsByUsername("petar")).thenReturn(false);

        assertThat(service.isUsernameAvailable("petar")).isTrue();
    }

    @Test
    void aHandleSomebodyHoldsIsNot() {
        when(users.existsByUsername("petar")).thenReturn(true);

        assertThat(service.isUsernameAvailable("petar")).isFalse();
    }

    @Test
    void aReservedHandleIsUnavailableWithoutALookup() {
        assertThat(service.isUsernameAvailable("settings")).isFalse();

        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    void aHandleTheColumnWouldRefuseIsUnavailable() {
        assertThat(service.isUsernameAvailable("ma")).isFalse();
        assertThat(service.isUsernameAvailable("Marko")).isFalse();
        assertThat(service.isUsernameAvailable("marko!")).isFalse();
        assertThat(service.isUsernameAvailable("m".repeat(31))).isFalse();

        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    void theSettingsCarryThePrivacyFlag() {
        when(privacy.of(ID)).thenReturn(new PrivacyDto(false));

        UserSettingsDto settings = service.settings(marko);

        assertThat(settings.username()).isEqualTo("marko");
        assertThat(settings.email()).isEqualTo("marko@example.com");
        assertThat(settings.privacy().showJobPostings()).isFalse();
    }

    @Test
    void aProfileUpdateNormalisesTheWayRegistrationDoes() {
        UserSettingsDto updated = service.updateProfile(marko,
                new UpdateProfileRequest("  Marko Markovic ", "   ", null, " Beograd ", TestFixtures.contacts()));

        assertThat(marko.getFullName()).isEqualTo("Marko Markovic");
        assertThat(marko.getBio()).isNull();
        assertThat(marko.getLocation()).isEqualTo("Beograd");
        assertThat(marko.getOtherContactInfo()).hasSize(2);
        assertThat(updated.fullName()).isEqualTo("Marko Markovic");
    }

    @Test
    void anAbsentFullNameIsStoredEmpty() {
        service.updateProfile(marko, new UpdateProfileRequest(null, null, null, null, null));

        assertThat(marko.getFullName()).isEmpty();
        assertThat(marko.getOtherContactInfo()).isEmpty();
    }

    @Test
    void aNullAvatarKeepsTheCurrentOne() {
        marko.setAvatarUrl("http://localhost:9000/spona-avatars/old.png");

        service.updateProfile(marko, new UpdateProfileRequest("Marko", null, null, null, null));

        assertThat(marko.getAvatarUrl()).isEqualTo("http://localhost:9000/spona-avatars/old.png");
        verify(avatars, never()).replace(any(), any());
    }

    @Test
    void aNewAvatarReplacesTheCurrentOne() {
        marko.setAvatarUrl("old-url");
        when(avatars.replace("old-url", "data:image/png;base64,AAAA")).thenReturn("new-url");

        UserSettingsDto updated = service.updateProfile(marko,
                new UpdateProfileRequest("Marko", null, "data:image/png;base64,AAAA", null, null));

        assertThat(marko.getAvatarUrl()).isEqualTo("new-url");
        assertThat(updated.avatarUrl()).isEqualTo("new-url");
    }

    @Test
    void renamingToTheHandleAlreadyHeldChangesNothing() {
        UserSettingsDto updated = service.changeUsername(marko, "marko");

        assertThat(updated.username()).isEqualTo("marko");
        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    void aReservedHandleIsRefusedWithoutALookup() {
        assertThatThrownBy(() -> service.changeUsername(marko, "settings"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.HANDLE_RESERVED));

        assertThat(marko.getUsername()).isEqualTo("marko");
        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    void aTakenHandleIsRefused() {
        when(users.existsByUsername("petar")).thenReturn(true);

        assertThatThrownBy(() -> service.changeUsername(marko, "petar"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.USERNAME_TAKEN));

        assertThat(marko.getUsername()).isEqualTo("marko");
    }

    @Test
    void aFreeHandleIsWrittenAndAnswered() {
        UserSettingsDto updated = service.changeUsername(marko, "petar");

        assertThat(marko.getUsername()).isEqualTo("petar");
        assertThat(updated.username()).isEqualTo("petar");
    }

    @Test
    void anEmailChangeIssuesAChangeTokenForTheNormalisedAddress() {
        service.requestEmailChange(marko, new ChangeEmailRequest("  Novi@Primer.RS ", PASSWORD));

        verify(verification).issue(marko, EmailVerificationPurpose.CHANGE, "novi@primer.rs");
        assertThat(marko.getEmail()).isEqualTo("marko@example.com");
    }

    @Test
    void anEmailChangeWithTheWrongPasswordIsRefusedBeforeTheAddressIsLookedUp() {
        assertThatThrownBy(() -> service.requestEmailChange(marko,
                new ChangeEmailRequest("zauzeto@primer.rs", "pogresna")))
                .isInstanceOf(ForbiddenException.class);

        verify(users, never()).existsByEmailIgnoreCase(anyString());
        verify(verification, never()).issue(any(), any(), any());
    }

    @Test
    void anEmailChangeToARegisteredAddressIsRefused() {
        when(users.existsByEmailIgnoreCase("zauzeto@primer.rs")).thenReturn(true);

        assertThatThrownBy(() -> service.requestEmailChange(marko,
                new ChangeEmailRequest("zauzeto@primer.rs", PASSWORD)))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.EMAIL_TAKEN));

        verify(verification, never()).issue(any(), any(), any());
    }

    @Test
    void anEmailChangeToTheCurrentAddressIsRefusedUnderEmail() {
        assertThatThrownBy(() -> service.requestEmailChange(marko,
                new ChangeEmailRequest("MARKO@example.com", PASSWORD)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("email"));

        verify(verification, never()).issue(any(), any(), any());
    }

    @Test
    void aPasswordChangeRewritesTheDigestAndSpendsEveryLiveResetLink() {
        PasswordResetToken first = TestFixtures.resetToken(marko, "plain-one");
        PasswordResetToken second = TestFixtures.resetToken(marko, "plain-two");
        when(resetTokens.findByUserAndConsumedAtIsNull(marko)).thenReturn(List.of(first, second));
        when(passwords.encode("NovaLozinka1")).thenReturn("new-digest");

        service.changePassword(marko, new ChangePasswordRequest(PASSWORD, "NovaLozinka1"));

        assertThat(marko.getPasswordDigest()).isEqualTo("new-digest");
        assertThat(first.isLive(Instant.now())).isFalse();
        assertThat(second.isLive(Instant.now())).isFalse();
    }

    @Test
    void aPasswordChangeWithTheWrongPasswordWritesNothing() {
        String before = marko.getPasswordDigest();

        assertThatThrownBy(() -> service.changePassword(marko, new ChangePasswordRequest("pogresna", "NovaLozinka1")))
                .isInstanceOf(ForbiddenException.class);

        assertThat(marko.getPasswordDigest()).isEqualTo(before);
        verify(passwords, never()).encode(any());
        verify(resetTokens, never()).findByUserAndConsumedAtIsNull(any());
    }

    @Test
    void deletingAnAccountDeletesTheOrganizationsItOwnsFirst() {
        UUID ownedId = UUID.randomUUID();
        Organization owned = TestFixtures.organization("owned-org");
        ReflectionTestUtils.setField(owned, "id", ownedId);
        owned.setAvatarUrl("org-avatar");
        marko.setAvatarUrl("user-avatar");
        when(roles.findLiveOrganizationIds(ID, OrganizationRole.OWNER)).thenReturn(List.of(ownedId));
        when(organizations.findAllById(List.of(ownedId))).thenReturn(List.of(owned));

        service.deleteAccount(marko, PASSWORD);

        InOrder order = inOrder(roles, organizations, users);
        order.verify(roles).findLiveOrganizationIds(ID, OrganizationRole.OWNER);
        order.verify(organizations).deleteAll(List.of(owned));
        order.verify(users).delete(marko);
        verify(avatars).discard("org-avatar");
        verify(avatars).discard("user-avatar");
    }

    @Test
    void deletingWithTheWrongPasswordDeletesNothing() {
        assertThatThrownBy(() -> service.deleteAccount(marko, "pogresna"))
                .isInstanceOf(ForbiddenException.class);

        verify(roles, never()).findLiveOrganizationIds(any(), any());
        verify(organizations, never()).deleteAll(any());
        verify(users, never()).delete(any());
        verify(avatars, never()).discard(any());
    }

    @Test
    void aCallerWhoseRowIsGoneIsASessionThatIsGone() {
        User ghost = TestFixtures.userWithId("ghost", UUID.randomUUID());

        assertThatThrownBy(() -> service.changeUsername(ghost, "petar"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
