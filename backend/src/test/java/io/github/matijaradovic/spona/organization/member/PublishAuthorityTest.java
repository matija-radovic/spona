package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PublishAuthorityTest {

    @ParameterizedTest
    @CsvSource({
            "OWNER,     true,  true,  true,  true, true",
            "ADMIN,     true,  true,  true,  true, false",
            "PUBLISHER, true,  true,  false, true, false",
            "EDITOR,    true,  false, false, true, false",
            "RECRUITER, false, true,  false, true, false"
    })
    void answersTheMatrixForEveryRole(OrganizationRole role, boolean content, boolean jobs,
            boolean profile, boolean settings, boolean management) {
        assertThat(PublishAuthority.canPublishContent(role)).isEqualTo(content);
        assertThat(PublishAuthority.canManageJobPostings(role)).isEqualTo(jobs);
        assertThat(PublishAuthority.canEditProfile(role)).isEqualTo(profile);
        assertThat(PublishAuthority.canReadSettings(role)).isEqualTo(settings);
        assertThat(PublishAuthority.canManageOrganization(role)).isEqualTo(management);
    }

    @Test
    void aNonMemberIsAuthorisedForNothing() {
        assertThat(PublishAuthority.canPublishContent(null)).isFalse();
        assertThat(PublishAuthority.canManageJobPostings(null)).isFalse();
        assertThat(PublishAuthority.canEditProfile(null)).isFalse();
        assertThat(PublishAuthority.canReadSettings(null)).isFalse();
        assertThat(PublishAuthority.canManageOrganization(null)).isFalse();
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
}
