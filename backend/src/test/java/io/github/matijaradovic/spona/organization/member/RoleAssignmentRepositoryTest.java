package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

class RoleAssignmentRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void findsTheLiveRoleOfAMember() {
        User member = users.save(TestFixtures.user("rolemember"));
        Organization org = organizations.save(TestFixtures.organization("role-org"));
        members.save(TestFixtures.membership(member, org));
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.RECRUITER));

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(org.getId(), member.getId()))
                .get()
                .extracting(row -> row.getRole())
                .isEqualTo(OrganizationRole.RECRUITER);
    }

    @ParameterizedTest
    @EnumSource(OrganizationRole.class)
    void storesAndReadsBackEveryRoleTheCodeKnows(OrganizationRole role) {
        String handle = role.name().toLowerCase(Locale.ROOT);
        User member = users.save(TestFixtures.user("roleeach" + handle));
        Organization org = organizations.save(TestFixtures.organization("role-each-" + handle));
        members.save(TestFixtures.membership(member, org));
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, role));
        entityManager.clear();

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(org.getId(), member.getId()))
                .get()
                .extracting(row -> row.getRole())
                .isEqualTo(role);
    }

    @Test
    void findsNothingForSomebodyWhoIsNotAMember() {
        User stranger = users.save(TestFixtures.user("rolestranger"));
        Organization org = organizations.save(TestFixtures.organization("role-stranger-org"));

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(
                org.getId(), stranger.getId())).isEmpty();
    }

    @Test
    void stopsSeeingARevokedRow() {
        User member = users.save(TestFixtures.user("rolerevoked"));
        Organization org = organizations.save(TestFixtures.organization("role-revoked-org"));
        members.save(TestFixtures.membership(member, org));
        RoleAssignment granted = roles
                .saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.EDITOR));

        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(
                org.getId(), member.getId())).isEmpty();
    }

    @Test
    void readsTheReplacementOnceTheFirstRowIsRevoked() {
        User member = users.save(TestFixtures.user("rolereplaced"));
        Organization org = organizations.save(TestFixtures.organization("role-replaced-org"));
        members.save(TestFixtures.membership(member, org));
        RoleAssignment granted = roles
                .saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.EDITOR));

        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.ADMIN));

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(org.getId(), member.getId()))
                .get()
                .extracting(row -> row.getRole())
                .isEqualTo(OrganizationRole.ADMIN);
    }

    @Test
    void readsTheLiveRoleAsAValue() {
        User member = users.save(TestFixtures.user("roleprojected"));
        Organization org = organizations.save(TestFixtures.organization("role-projected-org"));
        members.save(TestFixtures.membership(member, org));
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.PUBLISHER));
        entityManager.clear();

        assertThat(roles.findLiveRole(org.getId(), member.getId())).contains(OrganizationRole.PUBLISHER);
    }

    @Test
    void theLiveRoleValueSkipsRevokedRowsAndStrangers() {
        User member = users.save(TestFixtures.user("rolehistory"));
        User stranger = users.save(TestFixtures.user("rolehistorystranger"));
        Organization org = organizations.save(TestFixtures.organization("role-history-org"));
        members.save(TestFixtures.membership(member, org));
        RoleAssignment granted = roles
                .saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.EDITOR));
        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);

        assertThat(roles.findLiveRole(org.getId(), member.getId())).isEmpty();
        assertThat(roles.findLiveRole(org.getId(), stranger.getId())).isEmpty();

        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.ADMIN));

        assertThat(roles.findLiveRole(org.getId(), member.getId())).contains(OrganizationRole.ADMIN);
    }

    @Test
    void refusesASecondLiveRowForTheSamePair() {
        User member = users.save(TestFixtures.user("roletwice"));
        Organization org = organizations.save(TestFixtures.organization("role-twice-org"));
        members.save(TestFixtures.membership(member, org));
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.EDITOR));

        assertThatThrownBy(() -> roles
                .saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
