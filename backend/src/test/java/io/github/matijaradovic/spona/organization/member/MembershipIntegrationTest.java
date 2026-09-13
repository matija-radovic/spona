package io.github.matijaradovic.spona.organization.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class MembershipIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(OrganizationRole.class)
    void everyRoleReadsTheRoster(OrganizationRole role) throws Exception {
        String name = role.name().toLowerCase(Locale.ROOT);
        Organization organization = organizations.save(TestFixtures.organization("org-roster-" + name));
        User reader = member(organization, "rosterread" + name, role);

        mvc.perform(as(get("/organizations/org-roster-" + name + "/members"), reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(reader.getId().toString()))
                .andExpect(jsonPath("$[0].role").value(role.name()));
    }

    @Test
    void theRosterIsInJoinOrderWithEachMembersLiveRole() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-roster"));
        User owner = member(organization, "rosterowner", OrganizationRole.OWNER);
        User editor = member(organization, "rostereditor", OrganizationRole.EDITOR);
        User admin = member(organization, "rosteradmin", OrganizationRole.ADMIN);
        joined(owner, organization, 30);
        joined(editor, organization, 20);
        joined(admin, organization, 10);
        entityManager.clear();

        mvc.perform(as(get("/organizations/org-roster/members"), editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value("rosterowner"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].fullName").value(""))
                .andExpect(jsonPath("$[0].avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].joinedAt").isString())
                .andExpect(jsonPath("$[1].username").value("rostereditor"))
                .andExpect(jsonPath("$[1].role").value("EDITOR"))
                .andExpect(jsonPath("$[2].username").value("rosteradmin"))
                .andExpect(jsonPath("$[2].role").value("ADMIN"));
    }

    @Test
    void aNonMemberIs403OnTheRoster() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-roster-closed"));
        member(organization, "closedowner", OrganizationRole.OWNER);
        User outsider = verified("closedoutsider");

        mvc.perform(as(get("/organizations/org-roster-closed/members"), outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void aGuestIs401OnTheRosterAndOnMyOrganizations() throws Exception {
        organizations.save(TestFixtures.organization("org-roster-guest"));

        mvc.perform(get("/organizations/org-roster-guest/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/somebody/organizations")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownSlugIs404OnTheRoster() throws Exception {
        User reader = verified("rosternobody");

        mvc.perform(as(get("/organizations/nepostojeci/members"), reader)).andExpect(status().isNotFound());
    }

    @Test
    void theOwnerMakesAnAdminAnEditorAndTheRevokedRowStays() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-demote"));
        User owner = member(organization, "demoteowner", OrganizationRole.OWNER);
        User admin = member(organization, "demoteadmin", OrganizationRole.ADMIN);
        entityManager.clear();

        mvc.perform(changeRole(owner, "org-demote", admin, "EDITOR")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(organization.getId(), admin.getId()))
                .hasValueSatisfying(row -> {
                    assertThat(row.getRole()).isEqualTo(OrganizationRole.EDITOR);
                    assertThat(row.getAssignedBy().getId()).isEqualTo(owner.getId());
                });
        assertThat(count("select count(*) from role_assignment where user_id = :id", admin.getId())).isEqualTo(2);
        assertThat(count("select count(*) from role_assignment where user_id = :id and revoked_at is not null",
                admin.getId())).isEqualTo(1);
    }

    @Test
    void anAdminMakesAnEditorARecruiter() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-shuffle"));
        User admin = member(organization, "shuffleadmin", OrganizationRole.ADMIN);
        User editor = member(organization, "shuffleeditor", OrganizationRole.EDITOR);
        entityManager.clear();

        mvc.perform(changeRole(admin, "org-shuffle", editor, "RECRUITER")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(roles.findLiveRole(organization.getId(), editor.getId())).contains(OrganizationRole.RECRUITER);
    }

    @Test
    void anAdminIsRefusedOnAnotherAdminAndChangesNothing() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-peers"));
        User admin = member(organization, "peeradmin", OrganizationRole.ADMIN);
        User other = member(organization, "peerother", OrganizationRole.ADMIN);

        mvc.perform(changeRole(admin, "org-peers", other, "EDITOR")).andExpect(status().isForbidden());
        mvc.perform(removal(admin, "org-peers", other)).andExpect(status().isForbidden());

        assertThat(roles.findLiveRole(organization.getId(), other.getId())).contains(OrganizationRole.ADMIN);
        assertThat(members.existsById(new OrganizationMemberId(other.getId(), organization.getId()))).isTrue();
    }

    @Test
    void anAdminMayNotGrantAdmin() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-no-promotion"));
        User admin = member(organization, "nopromoadmin", OrganizationRole.ADMIN);
        User editor = member(organization, "nopromoeditor", OrganizationRole.EDITOR);

        mvc.perform(changeRole(admin, "org-no-promotion", editor, "ADMIN")).andExpect(status().isForbidden());

        assertThat(roles.findLiveRole(organization.getId(), editor.getId())).contains(OrganizationRole.EDITOR);
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "PUBLISHER", "EDITOR", "RECRUITER" })
    void aRoleBelowAdminChangesAndRemovesNobody(OrganizationRole role) throws Exception {
        String name = role.name().toLowerCase(Locale.ROOT);
        String slug = "org-below-" + name;
        Organization organization = organizations.save(TestFixtures.organization(slug));
        User caller = member(organization, "below" + name, role);
        User recruiter = member(organization, "belowtarget" + name, OrganizationRole.RECRUITER);

        mvc.perform(changeRole(caller, slug, recruiter, "EDITOR")).andExpect(status().isForbidden());
        mvc.perform(removal(caller, slug, recruiter)).andExpect(status().isForbidden());

        assertThat(roles.findLiveRole(organization.getId(), recruiter.getId())).contains(OrganizationRole.RECRUITER);
    }

    @Test
    void anAdminMayNotChangeOrRemoveTheirOwnRow() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-self-admin"));
        User admin = member(organization, "selfadmin", OrganizationRole.ADMIN);

        mvc.perform(changeRole(admin, "org-self-admin", admin, "EDITOR")).andExpect(status().isForbidden());
        mvc.perform(removal(admin, "org-self-admin", admin)).andExpect(status().isForbidden());

        assertThat(roles.findLiveRole(organization.getId(), admin.getId())).contains(OrganizationRole.ADMIN);
    }

    @Test
    void theOwnerRowAnswersLastOwnerWhoeverAimsAtIt() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-owner-row"));
        User owner = member(organization, "ownerrowowner", OrganizationRole.OWNER);
        User admin = member(organization, "ownerrowadmin", OrganizationRole.ADMIN);

        mvc.perform(changeRole(owner, "org-owner-row", owner, "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));
        mvc.perform(removal(owner, "org-owner-row", owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));
        mvc.perform(changeRole(admin, "org-owner-row", owner, "EDITOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));
        mvc.perform(removal(admin, "org-owner-row", owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));

        assertThat(roles.findLiveRole(organization.getId(), owner.getId())).contains(OrganizationRole.OWNER);
    }

    @Test
    void ownershipIsNeverAssignedThroughARoleChange() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-no-crown"));
        User owner = member(organization, "nocrownowner", OrganizationRole.OWNER);
        User admin = member(organization, "nocrownadmin", OrganizationRole.ADMIN);

        mvc.perform(changeRole(owner, "org-no-crown", admin, "OWNER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").exists());

        assertThat(roles.findLiveRole(organization.getId(), admin.getId())).contains(OrganizationRole.ADMIN);
    }

    @Test
    void theRoleAlreadyHeldIs204AndWritesNoRow() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-same-role"));
        User owner = member(organization, "sameroleowner", OrganizationRole.OWNER);
        User editor = member(organization, "sameroleeditor", OrganizationRole.EDITOR);
        entityManager.clear();

        mvc.perform(changeRole(owner, "org-same-role", editor, "EDITOR")).andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(count("select count(*) from role_assignment where user_id = :id", editor.getId())).isEqualTo(1);
    }

    @Test
    void aMemberIdThatIsNobodyHereIs404() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-ghost-member"));
        User owner = member(organization, "ghostowner", OrganizationRole.OWNER);
        User stranger = verified("ghoststranger");

        mvc.perform(changeRole(owner, "org-ghost-member", stranger, "EDITOR")).andExpect(status().isNotFound());
        mvc.perform(removal(owner, "org-ghost-member", stranger)).andExpect(status().isNotFound());
    }

    @Test
    void aRemovalDeletesTheMembershipAndEveryRoleRowOfThePairButNotTheAccount() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-removal"));
        User owner = member(organization, "removalowner", OrganizationRole.OWNER);
        User editor = member(organization, "removaleditor", OrganizationRole.EDITOR);
        entityManager.clear();

        mvc.perform(changeRole(owner, "org-removal", editor, "RECRUITER")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(removal(owner, "org-removal", editor)).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(members.existsById(new OrganizationMemberId(editor.getId(), organization.getId()))).isFalse();
        assertThat(count("select count(*) from role_assignment where user_id = :id", editor.getId())).isZero();
        assertThat(users.findById(editor.getId())).isPresent();
        assertThat(members.countByOrganization_Id(organization.getId())).isEqualTo(1);
    }

    @Test
    void aLeaveDeletesTheMembershipAndItsRoleRowsAndTheRosterNoLongerListsThem() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-left"));
        User owner = member(organization, "leftowner", OrganizationRole.OWNER);
        User editor = member(organization, "lefteditor", OrganizationRole.EDITOR);
        entityManager.clear();

        mvc.perform(as(delete("/users/lefteditor/organizations/org-left"), editor))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(members.existsById(new OrganizationMemberId(editor.getId(), organization.getId()))).isFalse();
        assertThat(count("select count(*) from role_assignment where user_id = :id", editor.getId())).isZero();
        assertThat(users.findById(editor.getId())).isPresent();
        mvc.perform(as(get("/organizations/org-left/members"), owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("leftowner"));
        mvc.perform(as(get("/organizations/org-left/settings"), editor)).andExpect(status().isForbidden());
    }

    @Test
    void theOwnersLeaveIs409LastOwnerAndChangesNothing() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-kept"));
        User owner = member(organization, "keptowner", OrganizationRole.OWNER);

        mvc.perform(as(delete("/users/keptowner/organizations/org-kept"), owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));

        assertThat(roles.findLiveRole(organization.getId(), owner.getId())).contains(OrganizationRole.OWNER);
        assertThat(members.countByOrganization_Id(organization.getId())).isEqualTo(1);
    }

    @Test
    void aLeaveFromAnOrganizationTheCallerIsNotInIs404() throws Exception {
        organizations.save(TestFixtures.organization("org-elsewhere"));
        User outsider = verified("elsewhereuser");

        mvc.perform(as(delete("/users/elsewhereuser/organizations/org-elsewhere"), outsider))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/users/elsewhereuser/organizations/nepostojeci"), outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void myOrganizationsAreInJoinOrderWithTheRoleBothDatesAndTheMemberCount() throws Exception {
        Organization early = organizations.save(TestFixtures.organization("org-mine-b"));
        Organization late = organizations.save(TestFixtures.organization("org-mine-a"));
        User me = verified("mineuser");
        join(me, early, OrganizationRole.OWNER);
        User other = member(late, "mineother", OrganizationRole.OWNER);
        join(me, late, OrganizationRole.EDITOR);
        joined(me, early, 30);
        joined(me, late, 10);
        entityManager.clear();

        mvc.perform(changeRole(other, "org-mine-a", me, "RECRUITER")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        String body = mvc.perform(as(get("/users/mineuser/organizations"), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].organization.actorType").value("org"))
                .andExpect(jsonPath("$[0].organization.id").value(early.getId().toString()))
                .andExpect(jsonPath("$[0].organization.handle").value("org-mine-b"))
                .andExpect(jsonPath("$[0].organization.name").value("Display org-mine-b"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].invitedBy").value(nullValue()))
                .andExpect(jsonPath("$[1].organization.handle").value("org-mine-a"))
                .andExpect(jsonPath("$[1].role").value("RECRUITER"))
                .andExpect(jsonPath("$[1].memberCount").value(2))
                .andReturn().getResponse().getContentAsString();

        Instant joinedAt = Instant.parse(JsonPath.read(body, "$[1].joinedAt"));
        Instant roleUpdatedAt = Instant.parse(JsonPath.read(body, "$[1].roleUpdatedAt"));
        assertThat(roleUpdatedAt).isAfter(joinedAt);
    }

    @Test
    void theInviterComesFromTheNewestAcceptedInvitation() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-invited"));
        User owner = member(organization, "invitedowner", OrganizationRole.OWNER);
        User former = verified("invitedformer");
        User me = member(organization, "inviteduser", OrganizationRole.EDITOR);
        accepted(organization, me, former, 40);
        accepted(organization, me, owner, 2);
        entityManager.clear();

        mvc.perform(as(get("/users/inviteduser/organizations"), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invitedBy.actorType").value("user"))
                .andExpect(jsonPath("$[0].invitedBy.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$[0].invitedBy.handle").value("invitedowner"))
                .andExpect(jsonPath("$[0].invitedBy.name").value(""))
                .andExpect(jsonPath("$[0].invitedBy.avatarUrl").value(nullValue()));
    }

    @Test
    void anotherAccountsOrganizationsAreNotYoursToReadOrLeave() throws Exception {
        User me = verified("nosyuser");
        verified("nosytarget");

        mvc.perform(as(get("/users/nosytarget/organizations"), me)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/users/nosytarget/organizations/org-any"), me)).andExpect(status().isForbidden());
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        join(user, organization, role);
        return user;
    }

    private void join(User user, Organization organization, OrganizationRole role) {
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
    }

    private void joined(User user, Organization organization, int daysAgo) {
        entityManager.createNativeQuery("update organization_member set joined_at = now() - make_interval(days => :days)"
                + " where user_id = :user and organization_id = :organization")
                .setParameter("days", daysAgo)
                .setParameter("user", user.getId())
                .setParameter("organization", organization.getId())
                .executeUpdate();
    }

    private void accepted(Organization organization, User invitee, User inviter, int daysAgo) {
        entityManager.createNativeQuery("insert into organization_invitations"
                + " (organization_id, role, invitee_user_id, invited_by_id, status, created_at, resolved_at)"
                + " values (:organization, 'EDITOR', :invitee, :inviter, 'ACCEPTED',"
                + " now() - make_interval(days => :days + 1), now() - make_interval(days => :days))")
                .setParameter("organization", organization.getId())
                .setParameter("invitee", invitee.getId())
                .setParameter("inviter", inviter.getId())
                .setParameter("days", daysAgo)
                .executeUpdate();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
    }

    private MockHttpServletRequestBuilder changeRole(User caller, String slug, User target, String role) {
        return as(put("/organizations/" + slug + "/members/" + target.getId()), caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\"}");
    }

    private MockHttpServletRequestBuilder removal(User caller, String slug, User target) {
        return as(delete("/organizations/" + slug + "/members/" + target.getId()), caller);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }
}
