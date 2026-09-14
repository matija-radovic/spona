package io.github.matijaradovic.spona.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class FeedIntegrationTest {

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
    private PostRepository posts;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void theCallerReadsTheirOwnAndTheirFolloweesPostsNewestFirst() throws Exception {
        User caller = verified("feedcaller");
        User friend = verified("feedfriend");
        User stranger = verified("feedstranger");
        Organization organization = organizations.save(TestFixtures.organization("feedorg"));
        User publisher = member(organization, "feedpublisher", OrganizationRole.PUBLISHER);
        follow(caller, friend);
        follow(caller, organization);
        UUID byFriend = post(friend, friend, 30);
        UUID byOrganization = post(publisher, organization, 20);
        UUID own = post(caller, caller, 10);
        post(stranger, stranger, 5);
        startFresh();

        feed(caller, 1, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].kind").value("post"))
                .andExpect(jsonPath("$.content[0].post.id").value(own.toString()))
                .andExpect(jsonPath("$.content[0].post.authorFollowedByViewer").value(false))
                .andExpect(jsonPath("$.content[0].post.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.content[1].post.id").value(byOrganization.toString()))
                .andExpect(jsonPath("$.content[1].post.author.actorType").value("org"))
                .andExpect(jsonPath("$.content[1].post.authorFollowedByViewer").value(true))
                .andExpect(jsonPath("$.content[1].post.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.content[2].post.id").value(byFriend.toString()))
                .andExpect(jsonPath("$.content[2].post.authorFollowedByViewer").value(true))
                .andExpect(jsonPath("$.content[2].post.viewerCanDelete").value(false));
    }

    @Test
    void membershipWithoutAFollowPutsNothingInTheFeed() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("feedmemberorg"));
        User caller = member(organization, "feedmember", OrganizationRole.EDITOR);
        post(caller, organization, 10);
        startFresh();

        feed(caller, 1, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aFollowedOrganizationsRowAnswersTheCallersLiveRole() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("feedroleorg"));
        User editor = member(organization, "feededitor", OrganizationRole.EDITOR);
        User recruiter = member(organization, "feedrecruiter", OrganizationRole.RECRUITER);
        follow(editor, organization);
        follow(recruiter, organization);
        post(editor, organization, 10);
        startFresh();

        feed(editor, 1, 10)
                .andExpect(jsonPath("$.content[0].post.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.content[0].post.viewerCanDelete").value(true));
        feed(recruiter, 1, 10)
                .andExpect(jsonPath("$.content[0].post.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.content[0].post.viewerCanDelete").value(false));
    }

    @Test
    void aTieOnCreatedAtIsBrokenByIdSoTwoPagesHoldBothPosts() throws Exception {
        User caller = verified("feedtiecaller");
        User friend = verified("feedtiefriend");
        follow(caller, friend);
        UUID first = post(friend, friend, 15);
        UUID second = post(friend, friend, 15);
        startFresh();

        String pageOne = feed(caller, 1, 1)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$._links.next.href").value("/api/feed?page=2&size=1"))
                .andReturn().getResponse().getContentAsString();
        String pageTwo = feed(caller, 2, 1)
                .andExpect(jsonPath("$.last").value(true))
                .andReturn().getResponse().getContentAsString();

        String onOne = JsonPath.read(pageOne, "$.content[0].post.id");
        String onTwo = JsonPath.read(pageTwo, "$.content[0].post.id");
        assertThat(List.of(onOne, onTwo)).containsExactlyInAnyOrder(first.toString(), second.toString());
    }

    @Test
    void anEmptyFeedIsA200WithAnEmptyLastPage() throws Exception {
        User caller = verified("feedlonely");

        feed(caller, 1, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    /** Sent under the context path, so links carry the prefix production serves. */
    private ResultActions feed(User caller, int page, int size) throws Exception {
        return mvc.perform(get("/api/feed").contextPath("/api")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(caller)));
    }

    private void follow(User follower, Actor followee) throws Exception {
        mvc.perform(put("/actors/" + followee.getId() + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(follower)))
                .andExpect(status().isNoContent());
    }

    /** Aged against the transaction's {@code now()}, so equal ages tie exactly. */
    private UUID post(User writer, Actor createdFor, int minutesAgo) {
        UUID id = posts.saveAndFlush(new Post(writer, createdFor, "Objava")).getId();
        entityManager.createNativeQuery("update post set created_at = now() - make_interval(mins => :minutes)"
                + " where id = :id")
                .setParameter("minutes", minutesAgo)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    private void startFresh() {
        entityManager.flush();
        entityManager.clear();
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.save(user);
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
        return user;
    }
}
