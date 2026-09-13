package io.github.matijaradovic.spona.actor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.follow.Follow;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.domain.Actor;
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
class FollowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private FollowRepository follows;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void followAndUnfollowAreIdempotentAndTheProfileCountsTheEdgeOnce() throws Exception {
        User caller = verified("fqcaller");
        User subject = verified("fqsubject");
        String follow = "/actors/" + subject.getId() + "/follow";

        mvc.perform(as(put(follow), caller)).andExpect(status().isNoContent());
        mvc.perform(as(put(follow), caller)).andExpect(status().isNoContent());

        mvc.perform(as(get("/users/fqsubject"), caller))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.isFollowedByViewer").value(true));

        mvc.perform(as(delete(follow), caller)).andExpect(status().isNoContent());
        mvc.perform(as(delete(follow), caller)).andExpect(status().isNoContent());

        mvc.perform(as(get("/users/fqsubject"), caller))
                .andExpect(jsonPath("$.followers").value(0))
                .andExpect(jsonPath("$.isFollowedByViewer").value(false));
    }

    @Test
    void aGuestCannotFollow() throws Exception {
        User subject = verified("fqguestless");

        mvc.perform(put("/actors/" + subject.getId() + "/follow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void followingYourselfIsA422AndFollowingNobodyIsA404() throws Exception {
        User caller = verified("fqself");

        mvc.perform(as(put("/actors/" + caller.getId() + "/follow"), caller))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(as(put("/actors/" + UUID.randomUUID() + "/follow"), caller))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/actors/" + UUID.randomUUID() + "/follow"), caller))
                .andExpect(status().isNotFound());
    }

    @Test
    void followersAreNewestEdgeFirstAndCarryTheCallersOwnEdge() throws Exception {
        User subject = verified("fqowner");
        User older = verified("fqolder");
        User newer = verified("fqnewer");
        User viewer = verified("fqviewer");
        edge(older, subject, 2);
        edge(newer, subject, 1);
        edge(viewer, older, 1);

        mvc.perform(as(get("/actors/" + subject.getId() + "/followers"), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].handle").value("fqnewer"))
                .andExpect(jsonPath("$.content[0].isFollowedByViewer").value(false))
                .andExpect(jsonPath("$.content[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.content[1].actorType").value("user"))
                .andExpect(jsonPath("$.content[1].isFollowedByViewer").value(true));
    }

    @Test
    void aGuestReadsAFollowerPageWithoutTheFlag() throws Exception {
        User subject = verified("fqpaged");
        edge(verified("fqfirst"), subject, 2);
        edge(verified("fqsecond"), subject, 1);

        mvc.perform(get("/actors/" + subject.getId() + "/followers").param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$._links.prev.href").exists())
                .andExpect(jsonPath("$.content[0].handle").value("fqfirst"))
                .andExpect(jsonPath("$.content[0].isFollowedByViewer").doesNotExist());
    }

    @Test
    void qMatchesNameOrHandleCaseFoldedAndAnEmptyQCountsWhatTheProfileCounts() throws Exception {
        User subject = verified("fqcounted");
        User named = verified("fqnamed");
        named.setFullName("Ana Jovanović");
        edge(named, subject, 2);
        edge(verified("fqhandle"), subject, 1);
        String followers = "/actors/" + subject.getId() + "/followers";

        mvc.perform(get(followers).param("q", "JOVAN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].handle").value("fqnamed"));
        mvc.perform(get(followers).param("q", " HANDLE "))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].handle").value("fqhandle"));
        mvc.perform(get(followers))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/users/fqcounted"))
                .andExpect(jsonPath("$.followers").value(2));
    }

    @Test
    void followingHoldsUsersAndOrganizationsAndQMatchesAnOrganizationName() throws Exception {
        User fan = verified("fqfan");
        Organization organization = organizations.save(TestFixtures.organization("fqorg"));
        edge(fan, organization, 2);
        edge(fan, verified("fqfriend"), 1);
        String following = "/actors/" + fan.getId() + "/following";

        mvc.perform(get(following))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].handle").value("fqfriend"))
                .andExpect(jsonPath("$.content[1].actorType").value("org"))
                .andExpect(jsonPath("$.content[1].handle").value("fqorg"))
                .andExpect(jsonPath("$.content[1].name").value("Display fqorg"));
        mvc.perform(get(following).param("q", "display"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].handle").value("fqorg"));
    }

    @Test
    void anOrganizationHasFollowersButNoFollowingList() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("fqnofollow"));
        edge(verified("fqorgfan"), organization, 1);

        mvc.perform(get("/actors/" + organization.getId() + "/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/actors/" + organization.getId() + "/following"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actors/" + UUID.randomUUID() + "/followers"))
                .andExpect(status().isNotFound());
    }

    @Test
    void userSearchServesIdCountsAndTheCallersEdgeInHandleOrder() throws Exception {
        User viewer = verified("fqsviewer");
        User bravo = verified("fqsbravo");
        User alpha = verified("fqsalpha");
        edge(viewer, bravo, 1);
        edge(alpha, bravo, 1);

        mvc.perform(as(get("/actors").param("type", "user").param("q", "FQS"), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].handle").value("fqsalpha"))
                .andExpect(jsonPath("$.content[0].following").value(1))
                .andExpect(jsonPath("$.content[0].isFollowedByViewer").value(false))
                .andExpect(jsonPath("$.content[1].id").value(bravo.getId().toString()))
                .andExpect(jsonPath("$.content[1].actorType").value("user"))
                .andExpect(jsonPath("$.content[1].followers").value(2))
                .andExpect(jsonPath("$.content[1].following").value(0))
                .andExpect(jsonPath("$.content[1].isFollowedByViewer").value(true))
                .andExpect(jsonPath("$.content[2].handle").value("fqsviewer"));
    }

    @Test
    void organizationSearchMatchesTheDisplayNameAndAGuestGetsNoFlag() throws Exception {
        Organization organization = TestFixtures.organization("fqsorg");
        organization.setDisplayName("Kvantni Tim");
        organizations.save(organization);

        mvc.perform(get("/actors").param("type", "org").param("q", "kvantni"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorType").value("org"))
                .andExpect(jsonPath("$.content[0].name").value("Kvantni Tim"))
                .andExpect(jsonPath("$.content[0].followers").value(0))
                .andExpect(jsonPath("$.content[0].following").doesNotExist())
                .andExpect(jsonPath("$.content[0].isFollowedByViewer").doesNotExist());
    }

    @Test
    void searchWithAnUnknownTypeIsA422() throws Exception {
        mvc.perform(get("/actors").param("type", "company"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.save(user);
    }

    private void edge(User follower, Actor followee, int daysAgo) {
        follows.saveAndFlush(new Follow(follower, followee));
        entityManager.createNativeQuery("update follows set created_at = now() - make_interval(days => :days)"
                + " where follower_id = :follower and followee_id = :followee")
                .setParameter("days", daysAgo)
                .setParameter("follower", follower.getId())
                .setParameter("followee", followee.getId())
                .executeUpdate();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User viewer) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(viewer));
    }
}
