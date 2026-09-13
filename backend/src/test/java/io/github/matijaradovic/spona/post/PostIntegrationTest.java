package io.github.matijaradovic.spona.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
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
class PostIntegrationTest {

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
    void anOrganizationPostIsManagedByRoleAndReadableByAnyone() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("postorg"));
        User publisher = member(organization, "postpublisher", OrganizationRole.PUBLISHER);
        User editor = member(organization, "posteditor", OrganizationRole.EDITOR);
        User stranger = verified("poststranger");

        String body = mvc.perform(as(post("/posts"), publisher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Objava organizacije ", organization.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(organization.getId().toString()))
                .andExpect(jsonPath("$.author.actorType").value("org"))
                .andExpect(jsonPath("$.content").value("Objava organizacije"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        String postId = JsonPath.read(body, "$.id");
        startFresh();

        mvc.perform(get("/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.handle").value("postorg"))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.authorFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanDelete").doesNotExist());
        mvc.perform(as(get("/posts/" + postId), stranger))
                .andExpect(jsonPath("$.authorFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanDelete").value(false));
        mvc.perform(as(get("/posts/" + postId), editor))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanDelete").value(true));
    }

    @Test
    void aRecruiterMayNotPublishAsTheOrganization() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("postrecruit"));
        User recruiter = member(organization, "postrecruiter", OrganizationRole.RECRUITER);

        mvc.perform(as(post("/posts"), recruiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Objava", organization.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAuthorEditsAndDeletesTheirOwnPost() throws Exception {
        User author = verified("postauthor");
        String postId = create(author, "Prvi post", author.getId());
        startFresh();

        mvc.perform(as(put("/posts/" + postId), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" Izmenjen post  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Izmenjen post"))
                .andExpect(jsonPath("$.viewerCanEdit").value(true));
        startFresh();

        mvc.perform(get("/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.handle").value("postauthor"))
                .andExpect(jsonPath("$.content").value("Izmenjen post"));

        mvc.perform(as(delete("/posts/" + postId), author)).andExpect(status().isNoContent());
        startFresh();

        mvc.perform(get("/posts/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void aStrangerMayNeitherEditNorDeleteSomeoneElsesPost() throws Exception {
        User author = verified("postowner");
        User stranger = verified("postother");
        String postId = create(author, "Moj post", author.getId());

        mvc.perform(as(put("/posts/" + postId), stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Tudji post\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(delete("/posts/" + postId), stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/posts/" + postId)).andExpect(jsonPath("$.content").value("Moj post"));
    }

    @Test
    void theContentBoundIsA422UnderContent() throws Exception {
        User author = verified("postbound");

        mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("a".repeat(Post.CONTENT_MAX + 1), author.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.content").exists());
        mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   ", author.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.content").exists());
        mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  " + "a".repeat(Post.CONTENT_MAX) + "  ", author.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void publishingAsNobodyIs404() throws Exception {
        User author = verified("postnobody");

        mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Objava", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aReaderSeesTheAuthorBioTheirFollowEdgeAndEveryComment() throws Exception {
        User author = verified("postbio");
        author.setBio("Backend developer");
        User reader = verified("postreader");
        String postId = create(author, "Post sa komentarima", author.getId());
        UUID root = comment(author, postId, null);
        comment(reader, postId, root);
        mvc.perform(as(put("/actors/" + author.getId() + "/follow"), reader)).andExpect(status().isNoContent());
        startFresh();

        mvc.perform(as(get("/posts/" + postId), reader))
                .andExpect(jsonPath("$.authorBio").value("Backend developer"))
                .andExpect(jsonPath("$.authorFollowedByViewer").value(true))
                .andExpect(jsonPath("$.commentCount").value(2));
    }

    private String create(User author, String content, UUID createdForId) throws Exception {
        String body = mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(content, createdForId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** {@code parentId} binds as a literal, never a null parameter Hibernate cannot type. */
    private UUID comment(User author, String postId, UUID parentId) {
        UUID id = UUID.randomUUID();
        String parent = parentId == null ? "null" : ":parent";
        var query = entityManager.createNativeQuery("insert into comment"
                + " (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at)"
                + " values (:id, 'Komentar', " + parent + ", :post, :author, :author, now())")
                .setParameter("id", id)
                .setParameter("post", UUID.fromString(postId))
                .setParameter("author", author.getId());
        if (parentId != null) {
            query.setParameter("parent", parentId);
        }
        query.executeUpdate();
        return id;
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

    private static String json(String content, UUID createdForId) {
        return "{\"content\":\"" + content + "\",\"createdForId\":\"" + createdForId + "\"}";
    }
}
