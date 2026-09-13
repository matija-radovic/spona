package io.github.matijaradovic.spona.actor.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class PrivacyIntegrationTest {

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
    private PrivacySettingsRepository settings;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aUserWritesTheirOwnFlagAndTheRowIsUpserted() throws Exception {
        User self = verified("privself");

        write(self.getId(), self, false).andExpect(status().isNoContent());
        assertThat(stored(self.getId())).isFalse();

        mvc.perform(get("/users/privself/settings").header(HttpHeaders.AUTHORIZATION, bearer(self)))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(false));

        write(self.getId(), self, true).andExpect(status().isNoContent());
        assertThat(stored(self.getId())).isTrue();
        assertThat(settings.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void anotherUsersFlagIs403() throws Exception {
        User subject = verified("privsubject");
        User stranger = verified("privstranger");

        write(subject.getId(), stranger, false).andExpect(status().isForbidden());

        assertThat(settings.findById(subject.getId())).isEmpty();
    }

    @Test
    void anUnknownActorIs404() throws Exception {
        User self = verified("privunknown");

        write(UUID.randomUUID(), self, false).andExpect(status().isNotFound());
    }

    @Test
    void anOwnerAndAnAdminWriteTheOrganizationsFlag() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("priv-admins-org"));
        User owner = member(organization, "privowner", OrganizationRole.OWNER);
        User admin = member(organization, "privadmin", OrganizationRole.ADMIN);

        write(organization.getId(), owner, false).andExpect(status().isNoContent());
        assertThat(stored(organization.getId())).isFalse();

        write(organization.getId(), admin, true).andExpect(status().isNoContent());
        assertThat(stored(organization.getId())).isTrue();
    }

    @Test
    void everyOtherRoleAndANonMemberAre403() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("priv-others-org"));
        User publisher = member(organization, "privpublisher", OrganizationRole.PUBLISHER);
        User editor = member(organization, "priveditor", OrganizationRole.EDITOR);
        User recruiter = member(organization, "privrecruiter", OrganizationRole.RECRUITER);
        User outsider = verified("privoutsider");

        write(organization.getId(), publisher, false).andExpect(status().isForbidden());
        write(organization.getId(), editor, false).andExpect(status().isForbidden());
        write(organization.getId(), recruiter, false).andExpect(status().isForbidden());
        write(organization.getId(), outsider, false).andExpect(status().isForbidden());

        assertThat(settings.findById(organization.getId())).isEmpty();
    }

    @Test
    void aRevokedAdminIs403() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("priv-revoked-org"));
        User former = member(organization, "privformer", OrganizationRole.ADMIN);
        RoleAssignment granted = roles
                .findByOrganizationIdAndUserIdAndRevokedAtIsNull(organization.getId(), former.getId())
                .orElseThrow();
        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);

        write(organization.getId(), former, false).andExpect(status().isForbidden());
    }

    @Test
    void aGuestIs401() throws Exception {
        User subject = verified("privguest");

        mvc.perform(put("/actors/" + subject.getId() + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":false}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions write(UUID actorId, User caller, boolean showJobPostings) throws Exception {
        return mvc.perform(put("/actors/" + actorId + "/privacy")
                .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":" + showJobPostings + "}"));
    }

    private boolean stored(UUID actorId) {
        entityManager.flush();
        entityManager.clear();
        return settings.findById(actorId).orElseThrow().isShowJobPostings();
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.sign(user);
    }
}
