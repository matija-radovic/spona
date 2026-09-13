package io.github.matijaradovic.spona.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationToken;
import io.github.matijaradovic.spona.auth.token.PasswordResetToken;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.invitation.OrganizationInvitation;
import io.github.matijaradovic.spona.organization.member.OrganizationMember;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.user.User;

/** Rows that satisfy every CHECK, so a test can say what it is testing. */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** The handle doubles as the local part of the address. `emailVerifiedAt` stays null. */
    public static User user(String username) {
        return new User(username, username + "@example.com", "digest-" + username);
    }

    /** The same user with its id assigned, for a test that signs a token without a database. */
    public static User userWithId(String username, UUID id) {
        User user = user(username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** A live token whose plaintext stays with the caller, because the row holds only the digest. */
    public static EmailVerificationToken token(User user, EmailVerificationPurpose purpose,
            String email, String plaintext) {
        return new EmailVerificationToken(user, purpose, email, SecureTokens.hash(plaintext),
                Instant.now().plus(purpose.lifetime()));
    }

    /** Live reset link, unlike a verification one may sit beside others just like it */
    public static PasswordResetToken resetToken(User user, String plaintext) {
        return new PasswordResetToken(user, SecureTokens.hash(plaintext),
                Instant.now().plus(PasswordResetToken.LIFETIME));
    }

    public static Organization organization(String slug) {
        return new Organization(slug, "Display " + slug, "Legal " + slug + " d.o.o.");
    }

    /** The row the role rows hang off, which `role_assignment`'s foreign key requires first. */
    public static OrganizationMember membership(User user, Organization organization) {
        return new OrganizationMember(user, organization);
    }

    /** A live grant, assigned by the member themselves so no third row is needed. */
    public static RoleAssignment roleAssignment(User user, Organization organization,
            OrganizationRole role) {
        return new RoleAssignment(user, organization, role, user);
    }

    /** invitation addressed by username, carrying the snapshot of the invitee's address. */
    public static OrganizationInvitation invitation(Organization organization, User invitee, User inviter,
            OrganizationRole role) {
        return new OrganizationInvitation(organization, role, invitee, invitee.getEmail(), inviter);
    }

    /** invitation thats addressed to an email address, bound to nobody */
    public static OrganizationInvitation addressInvitation(Organization organization, String email, User inviter,
            OrganizationRole role) {
        return new OrganizationInvitation(organization, role, null, email, inviter);
    }

    /** Two entries, one of them carrying the empty description the bound allows. */
    public static List<ContactEntry> contacts() {
        return List.of(
                new ContactEntry("email", "Office", "office@example.com"),
                new ContactEntry("phone", "", "+381110000000"));
    }
}
