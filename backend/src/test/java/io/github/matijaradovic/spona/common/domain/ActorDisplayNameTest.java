package io.github.matijaradovic.spona.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ActorDisplayNameTest {

    @Test
    void prefersTheFullNameWhenTheUserGaveOne() {
        User user = TestFixtures.user("marko");
        user.setFullName("Marko Marković");

        assertThat(user.resolveDisplayName()).isEqualTo("Marko Marković");
    }

    @Test
    void fallsBackToTheHandleWhenTheNameIsEmpty() {
        User user = TestFixtures.user("marko");

        assertThat(user.getFullName()).isEmpty();
        assertThat(user.resolveDisplayName()).isEqualTo("marko");
    }

    @Test
    void treatsWhitespaceAsNoName() {
        User user = TestFixtures.user("marko");
        user.setFullName("   ");

        assertThat(user.resolveDisplayName()).isEqualTo("marko");
    }

    @Test
    void neverSubstitutesForAnOrganization() {
        Organization organization = TestFixtures.organization("stolarija-novak");

        assertThat(organization.resolveDisplayName()).isEqualTo("Display stolarija-novak");
    }
}
