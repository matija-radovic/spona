package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.actor.privacy.PrivacySettings;
import io.github.matijaradovic.spona.actor.privacy.PrivacySettingsRepository;
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
class JobPostingsIntegrationTest {

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
    private JobRepository jobs;

    @Autowired
    private PrivacySettingsRepository privacySettings;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void aGuestReadsOnlyOpenPostingsNewestFirstEachCarryingItsStatus() throws Exception {
        User poster = verified("postingsguest");
        UUID older = seed(poster, poster, "javni-stariji", JobStatus.OPEN, 60, 60);
        UUID newer = seed(poster, poster, "javni-noviji", JobStatus.OPEN, 10, 10);
        seed(poster, poster, "javni-nacrt", JobStatus.DRAFT, 5, 5);
        seed(poster, poster, "javni-zatvoren", JobStatus.CLOSED, 5, 5);
        startFresh();

        String body = body(postings(poster.getId(), null));
        List<String> statuses = JsonPath.read(body, "$.content[*].status");
        Map<String, Object> createdFor = JsonPath.read(body, "$.content[0].createdFor");

        assertThat(ids(body)).containsExactly(newer, older);
        assertThat(statuses).containsExactly("OPEN", "OPEN");
        assertThat(createdFor).containsOnlyKeys("actorType").containsEntry("actorType", "user");
    }

    @Test
    void thePosterReadsEveryStatusNarrowedAndSortedAsAsked() throws Exception {
        User poster = verified("postingsowner");
        UUID a = seed(poster, poster, "vlastiti-a", JobStatus.OPEN, 30, 2);
        UUID b = seed(poster, poster, "vlastiti-b", JobStatus.DRAFT, 20, 12);
        UUID c = seed(poster, poster, "vlastiti-c", JobStatus.CLOSED, 10, 7);
        startFresh();

        assertThat(ids(poster.getId(), poster)).containsExactly(c, b, a);
        assertThat(ids(poster.getId(), poster, "status", "DRAFT")).containsExactly(b);
        assertThat(ids(poster.getId(), poster, "sort", "created_asc")).containsExactly(a, b, c);
        assertThat(ids(poster.getId(), poster, "sort", "updated_desc")).containsExactly(a, c, b);
        assertThat(ids(poster.getId(), poster, "sort", "updated_asc")).containsExactly(b, c, a);
        assertThat(ids(poster.getId(), poster, "status", "CLOSED", "sort", "created_desc")).containsExactly(c);
    }

    @Test
    void aStatusOrASortFromAGuestOrAStrangerIs400AndABadOneFromThePosterIs422() throws Exception {
        User poster = verified("postingsrefused");
        User stranger = verified("postingsrefusedstranger");
        seed(poster, poster, "odbijen-oglas", JobStatus.OPEN, 10, 10);
        startFresh();

        postings(poster.getId(), null, "status", "DRAFT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").doesNotExist());
        postings(poster.getId(), stranger, "sort", "created_asc").andExpect(status().isBadRequest());
        hide(poster);
        postings(poster.getId(), null, "status", "OPEN").andExpect(status().isBadRequest());
        postings(poster.getId(), poster, "status", "PENDING")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.status").exists());
        postings(poster.getId(), poster, "sort", "date_desc")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.sort").exists());
    }

    @Test
    void hiddenPostingsAnswerExactlyWhatNoPostingsAnswerAndStayVisibleToThePoster() throws Exception {
        User hiding = verified("postingshiding");
        User none = verified("postingsnone");
        User stranger = verified("postingshidingstranger");
        UUID open = seed(hiding, hiding, "sakriven-oglas", JobStatus.OPEN, 10, 10);
        hide(hiding);
        startFresh();

        String hidden = body(postings(hiding.getId(), stranger));
        String empty = body(postings(none.getId(), stranger));

        assertThat(hidden.replace(hiding.getId().toString(), "{actorId}"))
                .isEqualTo(empty.replace(none.getId().toString(), "{actorId}"));
        assertThat(ids(hiding.getId(), null)).isEmpty();
        assertThat(ids(hiding.getId(), hiding)).containsExactly(open);
    }

    @Test
    void anOrganizationsPostingsServeEveryStatusToARecruiterAndThePublicShapeToAnEditor() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("postingsorg"));
        User recruiter = member(organization, "postingsrecruiter", OrganizationRole.RECRUITER);
        User editor = member(organization, "postingseditor", OrganizationRole.EDITOR);
        UUID open = seed(recruiter, organization, "firmin-oglas", JobStatus.OPEN, 20, 20);
        UUID draft = seed(recruiter, organization, "firmin-nacrt", JobStatus.DRAFT, 10, 10);
        startFresh();

        assertThat(ids(organization.getId(), recruiter)).containsExactly(draft, open);
        assertThat(ids(organization.getId(), editor)).containsExactly(open);
        postings(organization.getId(), editor, "status", "DRAFT").andExpect(status().isBadRequest());
        postings(organization.getId(), null)
                .andExpect(jsonPath("$.content[0].createdFor.actorType").value("org"))
                .andExpect(jsonPath("$.content[0].createdFor.handle").value("postingsorg"));
    }

    @Test
    void tiedPostingsPageTotallyWithLinksCarryingTheSortAndAnUnknownActorIs404() throws Exception {
        User poster = verified("postingspager");
        List<UUID> seeded = List.of(
                seed(poster, poster, "isti-a", JobStatus.OPEN, 10, 10),
                seed(poster, poster, "isti-b", JobStatus.DRAFT, 10, 10),
                seed(poster, poster, "isti-c", JobStatus.CLOSED, 10, 10));
        startFresh();

        String first = body(postings(poster.getId(), poster, "sort", "created_asc", "page", "1", "size", "2"));
        List<UUID> paged = new ArrayList<>(ids(first));
        paged.addAll(ids(poster.getId(), poster, "sort", "created_asc", "page", "2", "size", "2"));
        String next = JsonPath.read(first, "$._links.next.href");

        assertThat(paged).containsExactlyInAnyOrderElementsOf(seeded);
        assertThat(next).isEqualTo("/api/actors/" + poster.getId() + "/jobs?page=2&size=2&sort=created_asc");
        postings(UUID.randomUUID(), null).andExpect(status().isNotFound());
    }

    /** Sent under the context path, so links carry the prefix production serves. */
    private ResultActions postings(UUID actorId, User viewer, String... pairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/actors/" + actorId + "/jobs").contextPath("/api");
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(viewer));
        }
        for (int i = 0; i < pairs.length; i += 2) {
            request.param(pairs[i], pairs[i + 1]);
        }
        return mvc.perform(request);
    }

    private List<UUID> ids(UUID actorId, User viewer, String... pairs) throws Exception {
        return ids(body(postings(actorId, viewer, pairs)));
    }

    private static List<UUID> ids(String body) {
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }

    private static String body(ResultActions response) throws Exception {
        return response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Aged against the transaction's {@code now()}, so equal ages tie exactly. */
    private UUID seed(User poster, Actor createdFor, String slug, JobStatus status, int createdMinutesAgo,
            int updatedMinutesAgo) {
        UUID id = jobs.saveAndFlush(TestFixtures.job(poster, createdFor, slug, status)).getId();
        entityManager.createNativeQuery("update job set created_at = now() - make_interval(mins => :created),"
                + " updated_at = now() - make_interval(mins => :updated) where id = :id")
                .setParameter("created", createdMinutesAgo)
                .setParameter("updated", updatedMinutesAgo)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    private void hide(Actor actor) {
        PrivacySettings settings = new PrivacySettings(actor.getId());
        settings.setShowJobPostings(false);
        privacySettings.saveAndFlush(settings);
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
