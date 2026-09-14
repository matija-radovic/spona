package io.github.matijaradovic.spona.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
class ReactionIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void reactionsShowOnThePostAndInTheBreakdown() throws Exception {
        User author = verified("reactauthor");
        User reader = verified("reactreader");
        String postId = newPost(author);

        react(reader, "post", postId, "😂").andExpect(status().isNoContent());
        react(reader, "post", postId, "👍").andExpect(status().isNoContent());
        react(reader, "post", postId, "👍").andExpect(status().isNoContent());
        react(author, "post", postId, "👍").andExpect(status().isNoContent());
        react(author, "post", postId, "🔥").andExpect(status().isNoContent());
        startFresh();

        mvc.perform(as(get("/posts/" + postId), reader))
                .andExpect(jsonPath("$.reactions.total").value(4))
                .andExpect(jsonPath("$.reactions.top[0].emoji").value("👍"))
                .andExpect(jsonPath("$.reactions.top[0].count").value(2))
                .andExpect(jsonPath("$.reactions.top[1].emoji").value("😂"))
                .andExpect(jsonPath("$.reactions.top[2].emoji").value("🔥"))
                .andExpect(jsonPath("$.reactions.othersCount").value(0))
                .andExpect(jsonPath("$.reactions.viewerReactions.length()").value(2));
        mvc.perform(get("/posts/" + postId))
                .andExpect(jsonPath("$.reactions.total").value(4))
                .andExpect(jsonPath("$.reactions.viewerReactions").doesNotExist());

        mvc.perform(get("/reactions/post/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].emoji").value("👍"))
                .andExpect(jsonPath("$.entries[0].count").value(2))
                .andExpect(jsonPath("$.entries[0].reactors[0].handle").value("reactauthor"))
                .andExpect(jsonPath("$.entries[0].reactors[1].handle").value("reactreader"))
                .andExpect(jsonPath("$.entries[0].reactors[0].actorType").value("user"))
                .andExpect(jsonPath("$.entries[1].emoji").value("😂"))
                .andExpect(jsonPath("$.entries[2].emoji").value("🔥"))
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void takingAReactionBackRemovesItAndIsIdempotent() throws Exception {
        User author = verified("unreactauthor");
        String postId = newPost(author);

        react(author, "post", postId, "🎉").andExpect(status().isNoContent());
        unreact(author, "post", postId, "🎉").andExpect(status().isNoContent());
        unreact(author, "post", postId, "🎉").andExpect(status().isNoContent());
        startFresh();

        mvc.perform(get("/reactions/post/" + postId))
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aCommentReactionShowsOnTheComment() throws Exception {
        User author = verified("commentreact");
        String postId = newPost(author);
        UUID commentId = comment(author, postId);

        react(author, "comment", commentId.toString(), "💡").andExpect(status().isNoContent());
        startFresh();

        mvc.perform(as(get("/comments/" + commentId), author))
                .andExpect(jsonPath("$.reactions.top[0].emoji").value("💡"))
                .andExpect(jsonPath("$.reactions.viewerReactions[0]").value("💡"));
        mvc.perform(get("/reactions/post/" + postId)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anEmojiOutsideThePaletteIsA422UnderEmoji() throws Exception {
        User author = verified("reactpalette");
        String postId = newPost(author);

        react(author, "post", postId, "🦄")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.emoji").exists());
        mvc.perform(as(put("/reactions/post/" + postId), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.emoji").exists());
    }

    @Test
    void aTargetThatNamesNothingIs404() throws Exception {
        User author = verified("reactnothing");
        String postId = newPost(author);

        react(author, "job", postId, "👍").andExpect(status().isNotFound());
        react(author, "comment", postId, "👍").andExpect(status().isNotFound());
        react(author, "post", UUID.randomUUID().toString(), "👍").andExpect(status().isNotFound());
        mvc.perform(get("/reactions/comment/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void aGuestMayReadButNotReact() throws Exception {
        User author = verified("reactguest");
        String postId = newPost(author);

        mvc.perform(put("/reactions/post/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emoji("👍")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/reactions/post/" + postId)).andExpect(status().isOk());
    }

    @Test
    void deletingAPostTakesItsReactionsAndItsCommentsReactions() throws Exception {
        User author = verified("reactdelpost");
        User reader = verified("reactdelreader");
        String postId = newPost(author);
        String keptPostId = newPost(author);
        UUID commentId = comment(author, postId);

        react(reader, "post", postId, "👍").andExpect(status().isNoContent());
        react(reader, "comment", commentId.toString(), "😂").andExpect(status().isNoContent());
        react(reader, "post", keptPostId, "👍").andExpect(status().isNoContent());
        startFresh();

        mvc.perform(as(delete("/posts/" + postId), author)).andExpect(status().isNoContent());

        assertThat(reactionsOn(UUID.fromString(postId), commentId)).isZero();
        assertThat(reactionsOn(UUID.fromString(keptPostId))).isEqualTo(1);
    }

    @Test
    void deletingARootCommentTakesItsRepliesReactions() throws Exception {
        User author = verified("reactdelroot");
        User reader = verified("reactdelroots");
        String postId = newPost(author);
        UUID rootId = comment(author, postId);
        UUID replyId = reply(author, postId, rootId);

        react(reader, "comment", rootId.toString(), "👍").andExpect(status().isNoContent());
        react(reader, "comment", replyId.toString(), "🔥").andExpect(status().isNoContent());
        react(reader, "post", postId, "🎉").andExpect(status().isNoContent());
        startFresh();

        mvc.perform(as(delete("/comments/" + rootId), author)).andExpect(status().isOk());

        assertThat(reactionsOn(rootId, replyId)).isZero();
        assertThat(reactionsOn(UUID.fromString(postId))).isEqualTo(1);
    }

    @Test
    void deletingAnAccountTakesTheReactionsOnWhatItPublished() throws Exception {
        User author = verified("reactdelacct");
        User reader = verified("reactdelkeeper");
        String postId = newPost(author);
        String keptPostId = newPost(reader);
        UUID commentId = comment(author, keptPostId);

        react(reader, "post", postId, "👍").andExpect(status().isNoContent());
        react(reader, "comment", commentId.toString(), "💡").andExpect(status().isNoContent());
        react(reader, "post", keptPostId, "🔥").andExpect(status().isNoContent());
        startFresh();

        deleteActor(author.getId());

        assertThat(reactionsOn(UUID.fromString(postId), commentId)).isZero();
        assertThat(reactionsOn(UUID.fromString(keptPostId))).isEqualTo(1);
    }

    @Test
    void deletingAnOrganizationTakesTheReactionsOnItsPosts() throws Exception {
        User member = verified("reactdelmember");
        Organization organization = organizations.saveAndFlush(TestFixtures.organization("reactdelorg"));
        UUID postId = organizationPost(member, organization);

        react(member, "post", postId.toString(), "👍").andExpect(status().isNoContent());
        startFresh();

        deleteActor(organization.getId());

        assertThat(reactionsOn(postId)).isZero();
    }

    private UUID reply(User author, String postId, UUID rootId) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("insert into comment"
                + " (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at)"
                + " values (:id, 'Odgovor', :root, :post, :author, :author, now())")
                .setParameter("id", id)
                .setParameter("root", rootId)
                .setParameter("post", UUID.fromString(postId))
                .setParameter("author", author.getId())
                .executeUpdate();
        return id;
    }

    private UUID organizationPost(User member, Organization organization) {
        UUID id = UUID.randomUUID();
        entityManager.flush();
        entityManager.createNativeQuery("insert into post (id, content, created_by_id, created_for_id, created_at)"
                + " values (:id, 'Objava', :member, :organization, now())")
                .setParameter("id", id)
                .setParameter("member", member.getId())
                .setParameter("organization", organization.getId())
                .executeUpdate();
        return id;
    }

    private void deleteActor(UUID actorId) {
        entityManager.createNativeQuery("delete from actor where id = :id")
                .setParameter("id", actorId)
                .executeUpdate();
    }

    private long reactionsOn(UUID... targetIds) {
        return ((Number) entityManager.createNativeQuery("select count(*) from reaction where target_id in (:ids)")
                .setParameter("ids", List.of(targetIds))
                .getSingleResult()).longValue();
    }

    private ResultActions react(User user, String targetType, String targetId, String emoji) throws Exception {
        return mvc.perform(as(put("/reactions/" + targetType + "/" + targetId), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(emoji(emoji)));
    }

    private ResultActions unreact(User user, String targetType, String targetId, String emoji) throws Exception {
        return mvc.perform(as(delete("/reactions/" + targetType + "/" + targetId), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(emoji(emoji)));
    }

    private String newPost(User author) throws Exception {
        String body = mvc.perform(as(post("/posts"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Objava\",\"createdForId\":\"" + author.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private UUID comment(User author, String postId) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("insert into comment"
                + " (id, content, response_to_comment_id, part_of_post_id, created_by_id, created_for_id, created_at)"
                + " values (:id, 'Komentar', null, :post, :author, :author, now())")
                .setParameter("id", id)
                .setParameter("post", UUID.fromString(postId))
                .setParameter("author", author.getId())
                .executeUpdate();
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

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }

    private static String emoji(String emoji) {
        return "{\"emoji\":\"" + emoji + "\"}";
    }
}
