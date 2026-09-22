package io.github.matijaradovic.spona.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.organization.OrganizationGate.Seat;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class OrganizationGateTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e2");

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);

    private final OrganizationGate gate = new OrganizationGate(organizations, roles);

    private final User marko = TestFixtures.userWithId("marko", CALLER_ID);
    private final Organization spona = TestFixtures.organization("spona");

    @BeforeEach
    void stubs() {
        ReflectionTestUtils.setField(spona, "id", ORG_ID);
        when(organizations.findBySlug("spona")).thenReturn(Optional.of(spona));
    }

    @Test
    void anUnknownSlugIs404BeforeAnyRoleIsRead() {
        assertThatThrownBy(() -> gate.seat("nepostojeci", marko, PublishAuthority::canReadSettings,
                ErrorCode.ORGANIZATION_NOT_A_MEMBER))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
        assertThatThrownBy(() -> gate.seat("nepostojeci", marko)).isInstanceOf(NotFoundException.class);

        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void aRoleThePredicateAllowsIsSeatedBesideItsOrganization() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.ADMIN));

        Seat seat = gate.seat("spona", marko, PublishAuthority::canManageMembers, ErrorCode.MEMBER_NOT_AN_ADMIN);

        assertThat(seat.organization()).isSameAs(spona);
        assertThat(seat.role()).isEqualTo(OrganizationRole.ADMIN);
    }

    @Test
    void aRoleThePredicateRefusesIs403WithTheRefusalAsItsCode() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        assertThatThrownBy(() -> gate.seat("spona", marko, PublishAuthority::canManageMembers,
                ErrorCode.MEMBER_NOT_AN_ADMIN))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_AN_ADMIN))
                .hasMessage("Only the owner or an admin may change members.");
    }

    @Test
    void noLiveRoleIsRefusedEvenByTheWidestPredicate() {
        assertThatThrownBy(() -> gate.seat("spona", marko, PublishAuthority::canReadSettings,
                ErrorCode.ORGANIZATION_NOT_A_MEMBER))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORGANIZATION_NOT_A_MEMBER));
    }

    @Test
    void theUnguardedSeatOfANonMemberCarriesANullRole() {
        Seat seat = gate.seat("spona", marko);

        assertThat(seat.organization()).isSameAs(spona);
        assertThat(seat.role()).isNull();
    }
}
