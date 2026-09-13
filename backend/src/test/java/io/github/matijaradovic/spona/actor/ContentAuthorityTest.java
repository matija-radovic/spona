package io.github.matijaradovic.spona.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ContentAuthorityTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e3");

    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final ContentAuthority authority = new ContentAuthority(roles);

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @Test
    void theCallersOwnActorPassesWithoutARoleLookup() {
        assertThat(authority.canManage(TestFixtures.userWithId("caller", CALLER_ID), caller)).isTrue();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void anotherUserNeverPasses() {
        assertThat(authority.canManage(TestFixtures.userWithId("other", OTHER_ID), caller)).isFalse();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "OWNER", "ADMIN", "PUBLISHER", "EDITOR" })
    void aContentRolePassesForTheOrganization(OrganizationRole role) {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));

        assertThat(authority.canManage(organization(), caller)).isTrue();
    }

    @Test
    void aRecruiterDoesNotPassForTheOrganization() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThat(authority.canManage(organization(), caller)).isFalse();
    }

    @Test
    void aNonMemberDoesNotPassForTheOrganization() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.empty());

        assertThat(authority.canManage(organization(), caller)).isFalse();
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
