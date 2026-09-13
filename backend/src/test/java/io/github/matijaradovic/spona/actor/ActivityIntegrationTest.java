package io.github.matijaradovic.spona.actor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
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
class ActivityIntegrationTest {

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
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void aGuestReadsPublicActivityWithNoAuthorityFlag() throws Exception {
        User owner = verified("actguest");
        UUID postId = post(owner, owner, "Prvi post", 5);

        mvc.perform(get("/actors/" + owner.getId() + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].kind").value("post"))
                .andExpect(jsonPath("$.content[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").doesNotExist());
    }

    @Test
    void kindAllInterleavesPostsAndCommentsNewestFirst() throws Exception {
        User owner = verified("actall");
        UUID older = post(owner, owner, "Stariji post", 30);
        UUID commentId = comment(owner, owner, older, null, "Komentar", 20);
        UUID newer = post(owner, owner, "Noviji post", 10);

        mvc.perform(get("/actors/" + owner.getId() + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.content[0].kind").value("post"))
                .andExpect(jsonPath("$.content[1].id").value(commentId.toString()))
                .andExpect(jsonPath("$.content[1].kind").value("comment"))
                .andExpect(jsonPath("$.content[2].id").value(older.toString()));
    }

    @Test
    void kindAllPaginatesAcrossTheUnion() throws Exception {
        User owner = verified("actpage");
        post(owner, owner, "Post 1", 30);
        post(owner, owner, "Post 2", 20);
        post(owner, owner, "Post 3", 10);

        mvc.perform(get("/actors/" + owner.getId() + "/activity").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/actors/" + owner.getId() + "/activity").param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void kindPostsServesCommentCountAsRootsPlusReplies() throws Exception {
        User owner = verified("actcount");
        UUID postId = post(owner, owner, "Post sa komentarima", 10);
        UUID root = comment(owner, owner, postId, null, "Root", 9);
        comment(owner, owner, postId, root, "Reply", 8);

        mvc.perform(get("/actors/" + owner.getId() + "/activity").param("kind", "posts"))
                .andExpect(jsonPath("$.content[0].commentCount").value(2));
    }

    @Test
    void kindCommentsMarksRepliesAndCountsOnlyDirectResponses() throws Exception {
        User owner = verified("actreply");
        UUID postId = post(owner, owner, "Post", 10);
        UUID root = comment(owner, owner, postId, null, "Root", 8);
        comment(owner, owner, postId, root, "Reply", 9);

        mvc.perform(get("/actors/" + owner.getId() + "/activity").param("kind", "comments"))
                .andExpect(jsonPath("$.content[0].id").value(root.toString()))
                .andExpect(jsonPath("$.content[0].isReply").value(false))
                .andExpect(jsonPath("$.content[0].responseCount").value(1))
                .andExpect(jsonPath("$.content[1].isReply").value(true))
                .andExpect(jsonPath("$.content[1].responseCount").value(0));
    }

    @Test
    void theOwnerMayEditTheirOwnPostButAnotherUserMayNot() throws Exception {
        User owner = verified("actmine");
        User stranger = verified("actstranger");
        post(owner, owner, "Moj post", 5);

        mvc.perform(as(get("/actors/" + owner.getId() + "/activity"), owner))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").value(true));
        mvc.perform(as(get("/actors/" + owner.getId() + "/activity"), stranger))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").value(false));
    }

    @Test
    void anEditorMayManageOrganizationContentButARecruiterMayNot() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("actorg"));
        User editor = member(organization, "acteditor", OrganizationRole.EDITOR);
        User recruiter = member(organization, "actrecruiter", OrganizationRole.RECRUITER);
        post(editor, organization, "Objava organizacije", 5);

        mvc.perform(as(get("/actors/" + organization.getId() + "/activity"), editor))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").value(true));
        mvc.perform(as(get("/actors/" + organization.getId() + "/activity"), recruiter))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").value(false));
    }

    @Test
    void anUnknownActorIs404() throws Exception {
        mvc.perform(get("/actors/" + UUID.randomUUID() + "/activity")).andExpect(status().isNotFound());
    }

    @Test
    void anInvalidKindIs422() throws Exception {
        User owner = verified("actbadkind");

        mvc.perform(get("/actors/" + owner.getId() + "/activity").param("kind", "bogus"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.kind").exists());
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

    private UUID post(User createdBy, Actor createdFor, String content, int minutesAgo) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("insert into post (id, content, created_by_id, created_for_id, created_at)"
                + " values (:id, :content, :createdBy, :createdFor, now() - make_interval(mins => :minutes))")
                .setParameter("id", id)
                .setParameter("content", content)
                .setParameter("createdBy", createdBy.getId())
                .setParameter("createdFor", createdFor.getId())
                .setParameter("minutes", minutesAgo)
                .executeUpdate();
        return id;
    }

    /** {@code responseToCommentId} binds as a literal, never a null parameter Hibernate cannot type. */
    private UUID comment(User createdBy, Actor createdFor, UUID postId, UUID responseToCommentId, String content,
            int minutesAgo) {
        UUID id = UUID.randomUUID();
        String parent = responseToCommentId == null ? "null" : ":parent";
        var query = entityManager.createNativeQuery("insert into comment"
                + " (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at)"
                + " values (:id, :content, " + parent + ", :post, :createdBy, :createdFor,"
                + " now() - make_interval(mins => :minutes))")
                .setParameter("id", id)
                .setParameter("content", content)
                .setParameter("post", postId)
                .setParameter("createdBy", createdBy.getId())
                .setParameter("createdFor", createdFor.getId())
                .setParameter("minutes", minutesAgo);
        if (responseToCommentId != null) {
            query.setParameter("parent", responseToCommentId);
        }
        query.executeUpdate();
        return id;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }
}
