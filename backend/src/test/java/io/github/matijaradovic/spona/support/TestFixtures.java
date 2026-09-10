package io.github.matijaradovic.spona.support;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

/** Rows that satisfy every CHECK, so a test can say what it is testing. */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** A verified user. The handle doubles as the local part of the address. */
    public static User user(String username) {
        return new User(username, username + "@example.com", "digest-" + username);
    }

    public static Organization organization(String slug) {
        return new Organization(slug, "Display " + slug, "Legal " + slug + " d.o.o.");
    }

    /** Two entries, one of them carrying the empty description the bound allows. */
    public static List<ContactEntry> contacts() {
        return List.of(
                new ContactEntry("email", "Office", "office@example.com"),
                new ContactEntry("phone", "", "+381110000000"));
    }
}
