package io.github.matijaradovic.spona.actor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.follow.Follow;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.domain.Actor;
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

/** The four audiences, on both subtypes: guest, stranger, the actor themselves, and each role */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private FollowRepository follows;

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private JwtService jwtService;

    @Test
    void aGuestReadsAUserProfileAndGetsNeitherA401NorTheViewerFields() throws Exception {
        User subject = verified("profsubject");
        subject.setFullName("Subject Person");
        subject.setLocation("Beograd");
        subject.setOtherContactInfo(TestFixtures.contacts());

        mvc.perform(get("/users/" + subject.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorType").value("user"))
                .andExpect(jsonPath("$.username").value("profsubject"))
                .andExpect(jsonPath("$.fullName").value("Subject Person"))
                .andExpect(jsonPath("$.location").value("Beograd"))
                .andExpect(jsonPath("$.otherContactInfo.length()").value(2))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.isFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanManageJobPostings").doesNotExist());
    }

    @Test
    void aUserProfileNeverCarriesTheAddress() throws Exception {
        User subject = verified("profnoemail");

        mvc.perform(get("/users/" + subject.getUsername()))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordDigest").doesNotExist());
    }

    @Test
    void aStrangerIsToldThereIsNoEdgeAndNoAuthority() throws Exception {
        User subject = verified("profstranged");
        User stranger = verified("profstranger");

        mvc.perform(as(get("/users/" + subject.getUsername()), stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(false));
    }

    @Test
    void aFollowerIsToldTheirOwnEdgeExists() throws Exception {
        User subject = verified("proffollowed");
        User follower = verified("proffollower");
        followEdge(follower, subject);

        mvc.perform(as(get("/users/" + subject.getUsername()), follower))
                .andExpect(jsonPath("$.isFollowedByViewer").value(true));

        mvc.perform(as(get("/users/" + follower.getUsername()), subject))
                .andExpect(jsonPath("$.isFollowedByViewer").value(false));
    }

    @Test
    void aPersonMayEditAndManageTheirOwnProfileAndNobodyElses() throws Exception {
        User subject = verified("profowner");

        mvc.perform(as(get("/users/" + subject.getUsername()), subject))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true))
                .andExpect(jsonPath("$.isFollowedByViewer").value(false));
    }

    @Test
    void servesBothCountsOffTheFollowsTable() throws Exception {
        User subject = verified("profcounts");
        followEdge(verified("profcountsone"), subject);
        followEdge(verified("profcountstwo"), subject);
        followEdge(subject, organizations.save(TestFixtures.organization("prof-counts-org")));

        mvc.perform(get("/users/" + subject.getUsername()))
                .andExpect(jsonPath("$.followers").value(2))
                .andExpect(jsonPath("$.following").value(1));
    }

    @Test
    void answers404ForAUsernameThatIsNobody() throws Exception {
        mvc.perform(get("/users/nepostojeci"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/users/nepostojeci"));
    }

    @Test
    void aGuestReadsAnOrganizationAndGetsNeitherA401NorTheViewerFields() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-guest-org"));
        subject.setDescription("Sve o nama.");
        subject.setWebsite("https://primer.rs");

        mvc.perform(get("/organizations/" + subject.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorType").value("org"))
                .andExpect(jsonPath("$.slug").value("prof-guest-org"))
                .andExpect(jsonPath("$.displayName").value("Display prof-guest-org"))
                .andExpect(jsonPath("$.description").value("Sve o nama."))
                .andExpect(jsonPath("$.website").value("https://primer.rs"))
                .andExpect(jsonPath("$.isFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanManageJobPostings").doesNotExist());
    }

    @Test
    void anOrganizationHasNoFollowingCountAndNoPrivacyFlag() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-shape-org"));
        followEdge(verified("profshapefollower"), subject);

        mvc.perform(get("/organizations/" + subject.getSlug()))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.following").doesNotExist())
                .andExpect(jsonPath("$.showJobPostings").doesNotExist())
                .andExpect(jsonPath("$.legalName").doesNotExist());
    }

    @Test
    void aNonMemberMayNeitherEditNorManage() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-outsider-org"));
        User outsider = verified("profoutsider");

        mvc.perform(as(get("/organizations/" + subject.getSlug()), outsider))
                .andExpect(jsonPath("$.isFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(false));
    }

    @Test
    void anOwnerMayEditAndManage() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-owner-org"));
        User owner = member(subject, "profowneruser", OrganizationRole.OWNER);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), owner))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true));
    }

    @Test
    void anAdminMayEditAndManage() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-admin-org"));
        User admin = member(subject, "profadminuser", OrganizationRole.ADMIN);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), admin))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true));
    }

    @Test
    void anEditorReachesNeitherTheSettingsGateNorThePostings() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-editor-org"));
        User editor = member(subject, "profeditoruser", OrganizationRole.EDITOR);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), editor))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(false));
    }

    @Test
    void aRecruiterManagesThePostingsWithoutTheProfile() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-recruiter-org"));
        User recruiter = member(subject, "profrecruiteruser", OrganizationRole.RECRUITER);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), recruiter))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true));
    }

    @Test
    void aPublisherManagesThePostingsWithoutTheProfile() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-publisher-org"));
        User publisher = member(subject, "profpublisheruser", OrganizationRole.PUBLISHER);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), publisher))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true));
    }

    @Test
    void aRevokedRoleIsNoRoleAtAll() throws Exception {
        Organization subject = organizations.save(TestFixtures.organization("prof-revoked-org"));
        User former = member(subject, "profformeruser", OrganizationRole.OWNER);
        RoleAssignment granted = roles
                .findByOrganizationIdAndUserIdAndRevokedAtIsNull(subject.getId(), former.getId())
                .orElseThrow();
        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);

        mvc.perform(as(get("/organizations/" + subject.getSlug()), former))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(false));
    }

    @Test
    void answers404ForASlugThatIsNobody() throws Exception {
        mvc.perform(get("/organizations/nepostojeci"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/organizations/nepostojeci"));
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.save(user);
    }

    private void followEdge(User follower, Actor followee) {
        follows.saveAndFlush(new Follow(follower, followee));
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
        return user;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User viewer) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(viewer));
    }
}
