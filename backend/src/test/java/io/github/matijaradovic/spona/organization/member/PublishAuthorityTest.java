package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PublishAuthorityTest {

    @ParameterizedTest
    @CsvSource({
            "OWNER,     true,  true,  true",
            "ADMIN,     true,  true,  true",
            "PUBLISHER, true,  true,  false",
            "EDITOR,    true,  false, false",
            "RECRUITER, false, true,  false"
    })
    void answersTheMatrixForEveryRole(OrganizationRole role, boolean content, boolean jobs,
            boolean profile) {
        assertThat(PublishAuthority.canPublishContent(role)).isEqualTo(content);
        assertThat(PublishAuthority.canManageJobPostings(role)).isEqualTo(jobs);
        assertThat(PublishAuthority.canEditProfile(role)).isEqualTo(profile);
    }

    @Test
    void aNonMemberIsAuthorisedForNothing() {
        assertThat(PublishAuthority.canPublishContent(null)).isFalse();
        assertThat(PublishAuthority.canManageJobPostings(null)).isFalse();
        assertThat(PublishAuthority.canEditProfile(null)).isFalse();
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
}
