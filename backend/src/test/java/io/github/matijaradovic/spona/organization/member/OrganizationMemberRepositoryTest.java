package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class OrganizationMemberRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void countsTheMembershipsOfOneOrganizationOnly() {
        User first = users.save(TestFixtures.user("countfirst"));
        User second = users.save(TestFixtures.user("countsecond"));
        Organization counted = organizations.save(TestFixtures.organization("count-org"));
        Organization other = organizations.save(TestFixtures.organization("count-other-org"));
        members.save(TestFixtures.membership(first, counted));
        members.save(TestFixtures.membership(second, counted));
        members.saveAndFlush(TestFixtures.membership(first, other));

        assertThat(members.countByOrganization_Id(counted.getId())).isEqualTo(2);
        assertThat(members.countByOrganization_Id(other.getId())).isEqualTo(1);
    }

    @Test
    void countsAMemberOnceHoweverManyRoleRowsTheyHave() {
        User member = users.save(TestFixtures.user("counthistory"));
        Organization org = organizations.save(TestFixtures.organization("count-history-org"));
        members.save(TestFixtures.membership(member, org));
        RoleAssignment first = roles
                .saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.EDITOR));
        first.revoke(first.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(first);
        roles.saveAndFlush(TestFixtures.roleAssignment(member, org, OrganizationRole.ADMIN));

        assertThat(members.countByOrganization_Id(org.getId())).isEqualTo(1);
    }
}
