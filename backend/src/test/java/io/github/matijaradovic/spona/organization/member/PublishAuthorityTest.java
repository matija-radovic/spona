package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PublishAuthorityTest {

    @ParameterizedTest
    @CsvSource({
            "OWNER,     true,  true,  true,  true, true,  true",
            "ADMIN,     true,  true,  true,  true, false, true",
            "PUBLISHER, true,  true,  false, true, false, false",
            "EDITOR,    true,  false, false, true, false, false",
            "RECRUITER, false, true,  false, true, false, false"
    })
    void answersTheMatrixForEveryRole(OrganizationRole role, boolean content, boolean jobs,
            boolean profile, boolean settings, boolean management, boolean members) {
        assertThat(PublishAuthority.canPublishContent(role)).isEqualTo(content);
        assertThat(PublishAuthority.canManageJobPostings(role)).isEqualTo(jobs);
        assertThat(PublishAuthority.canEditProfile(role)).isEqualTo(profile);
        assertThat(PublishAuthority.canReadSettings(role)).isEqualTo(settings);
        assertThat(PublishAuthority.canManageOrganization(role)).isEqualTo(management);
        assertThat(PublishAuthority.canManageMembers(role)).isEqualTo(members);
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER,     OWNER,     false",
            "OWNER,     ADMIN,     true",
            "OWNER,     PUBLISHER, true",
            "OWNER,     EDITOR,    true",
            "OWNER,     RECRUITER, true",
            "ADMIN,     OWNER,     false",
            "ADMIN,     ADMIN,     false",
            "ADMIN,     PUBLISHER, true",
            "ADMIN,     EDITOR,    true",
            "ADMIN,     RECRUITER, true",
            "PUBLISHER, OWNER,     false",
            "PUBLISHER, ADMIN,     false",
            "PUBLISHER, PUBLISHER, false",
            "PUBLISHER, EDITOR,    false",
            "PUBLISHER, RECRUITER, false",
            "EDITOR,    OWNER,     false",
            "EDITOR,    ADMIN,     false",
            "EDITOR,    PUBLISHER, false",
            "EDITOR,    EDITOR,    false",
            "EDITOR,    RECRUITER, false",
            "RECRUITER, OWNER,     false",
            "RECRUITER, ADMIN,     false",
            "RECRUITER, PUBLISHER, false",
            "RECRUITER, EDITOR,    false",
            "RECRUITER, RECRUITER, false"
    })
    void answersWhichRolesEveryRoleManages(OrganizationRole caller, OrganizationRole role, boolean manages) {
        assertThat(PublishAuthority.canManageRole(caller, role)).isEqualTo(manages);
    }

    @Test
    void aNonMemberIsAuthorisedForNothing() {
        assertThat(PublishAuthority.canPublishContent(null)).isFalse();
        assertThat(PublishAuthority.canManageJobPostings(null)).isFalse();
        assertThat(PublishAuthority.canEditProfile(null)).isFalse();
        assertThat(PublishAuthority.canReadSettings(null)).isFalse();
        assertThat(PublishAuthority.canManageOrganization(null)).isFalse();
        assertThat(PublishAuthority.canManageMembers(null)).isFalse();
    }

    @Test
    void aMissingRoleOnEitherSideManagesNothing() {
        assertThat(PublishAuthority.canManageRole(null, OrganizationRole.RECRUITER)).isFalse();
        assertThat(PublishAuthority.canManageRole(OrganizationRole.OWNER, null)).isFalse();
        assertThat(PublishAuthority.canManageRole(null, null)).isFalse();
    }

    @Test
    void theTwoColumnsDivergeInOppositeDirections() {
        assertThat(PublishAuthority.canPublishContent(OrganizationRole.EDITOR)).isTrue();
        assertThat(PublishAuthority.canManageJobPostings(OrganizationRole.EDITOR)).isFalse();

        assertThat(PublishAuthority.canPublishContent(OrganizationRole.RECRUITER)).isFalse();
        assertThat(PublishAuthority.canManageJobPostings(OrganizationRole.RECRUITER)).isTrue();
    }

    @Test
    void editingTheProfileIsNarrowerThanEitherColumn() {
        assertThat(PublishAuthority.canEditProfile(OrganizationRole.EDITOR)).isFalse();
        assertThat(PublishAuthority.canEditProfile(OrganizationRole.RECRUITER)).isFalse();
        assertThat(PublishAuthority.canEditProfile(OrganizationRole.ADMIN)).isTrue();
    }

    @Test
    void anAdminEditsTheProfileButDoesNotManageTheOrganization() {
        assertThat(PublishAuthority.canEditProfile(OrganizationRole.ADMIN)).isTrue();
        assertThat(PublishAuthority.canManageOrganization(OrganizationRole.ADMIN)).isFalse();
    }

    @Test
    void whoeverManagesMembersManagesSomeRoleAndNobodyElseManagesAny() {
        for (OrganizationRole caller : OrganizationRole.values()) {
            boolean managesSome = false;
            for (OrganizationRole role : OrganizationRole.values()) {
                managesSome |= PublishAuthority.canManageRole(caller, role);
            }
            assertThat(managesSome).as(caller.name()).isEqualTo(PublishAuthority.canManageMembers(caller));
        }
    }
}
