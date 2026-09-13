package io.github.matijaradovic.spona.comment;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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
class CommentIntegrationTest {

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
    void topRanksClampedReactionCountsThenOldestFirst() throws Exception {
        User author = verified("threadtop");
        UUID postId = newPost(author);
        UUID quiet = comment(author, postId, null, 60);
        UUID lukewarm = comment(author, postId, null, 50);
        UUID discussed = comment(author, postId, null, 40);
        UUID popular = comment(author, postId, null, 30);
        react(lukewarm, author, 2);
        react(discussed, author, 3);
        react(popular, author, 5);
        startFresh();

        mvc.perform(get("/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(ids(popular, discussed, quiet, lukewarm))))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/posts/" + postId + "/comments").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content[*].id", contains(ids(quiet, lukewarm))))
                .andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/posts/" + postId + "/comments").param("sort", "new"))
                .andExpect(jsonPath("$.content[*].id", contains(ids(popular, discussed, lukewarm, quiet))));
        mvc.perform(get("/posts/" + postId + "/comments").param("sort", "old"))
                .andExpect(jsonPath("$.content[*].id", contains(ids(quiet, lukewarm, discussed, popular))));
        mvc.perform(get("/posts/" + postId + "/comments").param("sort", "hot"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.sort").exists());
    }

    @Test
    void excludeWithholdsTheRootTheCommentBelongsTo() throws Exception {
        User author = verified("threadexcl");
        UUID postId = newPost(author);
        UUID first = comment(author, postId, null, 30);
        UUID second = comment(author, postId, null, 20);
        UUID reply = comment(author, postId, first, 10);
        startFresh();

        mvc.perform(get("/posts/" + postId + "/comments").param("exclude", reply.toString()))
                .andExpect(jsonPath("$.content[*].id", contains(ids(second))))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/posts/" + postId + "/comments").param("sort", "new").param("exclude", first.toString()))
                .andExpect(jsonPath("$.content[*].id", contains(ids(second))));
        mvc.perform(get("/posts/" + postId + "/comments").param("exclude", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.content[*].id", contains(ids(first, second))))
                .andExpect(jsonPath("$.content[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].responseCount").value(1));
    }

    @Test
    void theContextOfAReplyLocatesItAmongItsSiblings() throws Exception {
        User author = verified("threadctx");
        User reader = verified("threadrdr");
        UUID postId = newPost(author);
        UUID root = comment(author, postId, null, 60);
        UUID older = comment(reader, postId, root, 50);
        UUID anchored = comment(author, postId, root, 40);
        comment(reader, postId, root, 30);
        startFresh();

        mvc.perform(as(get("/comments/" + anchored).param("context", "1"), reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.id").value(anchored.toString()))
                .andExpect(jsonPath("$.comment.parentId").value(root.toString()))
                .andExpect(jsonPath("$.comment.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.parent.id").value(root.toString()))
                .andExpect(jsonPath("$.parent.responseCount").value(3))
                .andExpect(jsonPath("$.siblingIndex").value(1))
                .andExpect(jsonPath("$.siblingTotal").value(3));
        mvc.perform(get("/comments/" + root + "/responses").param("size", "2"))
                .andExpect(jsonPath("$.content[*].id", contains(ids(older, anchored))))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/comments/" + older + "/responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theContextOfARootRanksItAmongEveryRoot() throws Exception {
        User author = verified("threadroot");
        UUID postId = newPost(author);
        UUID older = comment(author, postId, null, 30);
        UUID newer = comment(author, postId, null, 20);
        react(newer, author, 3);
        startFresh();

        mvc.perform(get("/comments/" + older).param("context", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parent").value(nullValue()))
                .andExpect(jsonPath("$.siblingIndex").value(1))
                .andExpect(jsonPath("$.siblingTotal").value(2))
                .andExpect(jsonPath("$.comment.viewerCanEdit").doesNotExist());
        mvc.perform(get("/comments/" + older))
                .andExpect(jsonPath("$.id").value(older.toString()))
                .andExpect(jsonPath("$.siblingIndex").doesNotExist());
    }

    @Test
    void thePostScopedFormsAreServedPubliclyAndCheckContainment() throws Exception {
        User author = verified("threadalias");
        UUID postId = newPost(author);
        UUID otherPostId = newPost(author);
        UUID root = comment(author, postId, null, 20);
        comment(author, postId, root, 10);
        startFresh();

        mvc.perform(get("/posts/" + postId + "/comments/" + root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCount").value(1));
        mvc.perform(get("/posts/" + postId + "/comments/" + root + "/responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/posts/" + otherPostId + "/comments/" + root)).andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + otherPostId + "/comments/" + root + "/responses"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + UUID.randomUUID() + "/comments")).andExpect(status().isNotFound());
    }

    @Test
    void anOrganizationCommentIsManagedByRole() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("commentorg"));
        User editor = member(organization, "ceditor", OrganizationRole.EDITOR);
        User otherEditor = member(organization, "ceditortwo", OrganizationRole.EDITOR);
        User recruiter = member(organization, "crecruiter", OrganizationRole.RECRUITER);
        User stranger = verified("cstranger");
        UUID postId = newPost(stranger);

        String body = mvc.perform(as(post("/posts/" + postId + "/comments"), editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Komentar organizacije ", organization.getId(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.handle").value("commentorg"))
                .andExpect(jsonPath("$.content").value("Komentar organizacije"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.parentId").value(nullValue()))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        String commentId = JsonPath.read(body, "$.id");
        mvc.perform(as(post("/posts/" + postId + "/comments"), recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Komentar", organization.getId(), null)))
                .andExpect(status().isForbidden());
        startFresh();

        mvc.perform(get("/comments/" + commentId))
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanDelete").doesNotExist());
        mvc.perform(as(get("/comments/" + commentId), stranger))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanDelete").value(false));
        mvc.perform(as(put("/comments/" + commentId), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Tudji\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(put("/comments/" + commentId), otherEditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" Izmenjen \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Izmenjen"))
                .andExpect(jsonPath("$.author.handle").value("commentorg"));
    }

    @Test
    void repliesAreOneLevelDeep() throws Exception {
        User author = verified("threaddepth");
        UUID postId = newPost(author);
        UUID otherPostId = newPost(author);
        UUID root = comment(author, postId, null, 20);
        UUID reply = comment(author, postId, root, 10);
        UUID elsewhere = comment(author, otherPostId, null, 10);
        startFresh();

        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Dublje", author.getId(), reply)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.parentId").exists());
        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Tudje", author.getId(), elsewhere)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.parentId").exists());
        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Odgovor", author.getId(), root)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(root.toString()))
                .andExpect(jsonPath("$.responseCount").value(0));
        startFresh();

        mvc.perform(get("/comments/" + root)).andExpect(jsonPath("$.responseCount").value(2));
    }

    @Test
    void deletingARootTakesItsRepliesAndSaysHowMany() throws Exception {
        User author = verified("threaddel");
        UUID postId = newPost(author);
        UUID root = comment(author, postId, null, 30);
        UUID first = comment(author, postId, root, 20);
        UUID second = comment(author, postId, root, 10);
        startFresh();

        mvc.perform(as(delete("/comments/" + first), author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(1));
        startFresh();
        mvc.perform(as(delete("/comments/" + root), author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(2));
        startFresh();

        mvc.perform(get("/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(0));
        mvc.perform(get("/comments/" + second)).andExpect(status().isNotFound());
    }

    @Test
    void theContentBoundIsA422UnderContent() throws Exception {
        User author = verified("threadbound");
        UUID postId = newPost(author);

        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("a".repeat(Comment.CONTENT_MAX + 1), author.getId(), null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.content").exists());
        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   ", author.getId(), null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.content").exists());
        mvc.perform(as(post("/posts/" + postId + "/comments"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  " + "a".repeat(Comment.CONTENT_MAX) + "  ", author.getId(), null)))
                .andExpect(status().isCreated());
        mvc.perform(as(put("/comments/" + UUID.randomUUID()), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Novo\"}"))
                .andExpect(status().isNotFound());
    }

    private UUID newPost(User author) {
        return posts.saveAndFlush(new Post(author, author, "Objava")).getId();
    }

    /** {@code parentId} binds as a literal, never a null parameter Hibernate cannot type. */
    private UUID comment(User author, UUID postId, UUID parentId, int minutesAgo) {
        UUID id = UUID.randomUUID();
        String parent = parentId == null ? "null" : ":parent";
        var query = entityManager.createNativeQuery("insert into comment"
                + " (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at)"
                + " values (:id, 'Komentar', " + parent + ", :post, :author, :author,"
                + " now() - :minutes * interval '1 minute')")
                .setParameter("id", id)
                .setParameter("post", postId)
                .setParameter("author", author.getId())
                .setParameter("minutes", minutesAgo);
        if (parentId != null) {
            query.setParameter("parent", parentId);
        }
        query.executeUpdate();
        return id;
    }

    private void react(UUID commentId, User user, int count) {
        entityManager.createNativeQuery("insert into reaction (id, emoji, target_type_id, target_id, created_by_id)"
                + " select gen_random_uuid(), 'e' || n, target_type_id, :target, :user"
                + " from reaction_target_types, generate_series(1, :count) n where table_name = 'comments'")
                .setParameter("target", commentId)
                .setParameter("user", user.getId())
                .setParameter("count", count)
                .executeUpdate();
    }

    /** The next request loads its rows from the database, as a request in production does. */
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

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }

    private static String[] ids(UUID... ids) {
        return Arrays.stream(ids).map(id -> id.toString()).toArray(String[]::new);
    }

    private static String json(String content, UUID createdForId, UUID parentId) {
        return "{\"content\":\"" + content + "\",\"createdForId\":\"" + createdForId + "\",\"parentId\":"
                + (parentId == null ? "null" : "\"" + parentId + "\"") + "}";
    }
}
