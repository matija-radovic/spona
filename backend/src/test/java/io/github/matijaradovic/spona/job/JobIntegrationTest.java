package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
class JobIntegrationTest {

    private static final int DAY = 24 * 60;

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
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void theListServesOpenPostingsOnlyNewestFirstWithNoViewerField() throws Exception {
        User poster = verified("jobslist");
        UUID older = seed(poster, "stariji", 60, job -> { });
        UUID newer = seed(poster, "noviji", 10, job -> { });
        seed(poster, poster, "nacrt", JobStatus.DRAFT, 5, job -> { });
        seed(poster, poster, "zatvoren", JobStatus.CLOSED, 5, job -> { });
        startFresh();

        list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.content[1].id").value(older.toString()))
                .andExpect(jsonPath("$.content[0].keywords").isEmpty())
                .andExpect(jsonPath("$.content[0].createdFor.actorType").value("user"))
                .andExpect(jsonPath("$.content[0].createdFor.id").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].viewerCanEdit").doesNotExist());
        assertThat(ids("sort", "date_asc")).containsExactly(older, newer);
    }

    @Test
    void eachListFilterNarrowsAndAnUnstatedColumnMatchesNoOption() throws Exception {
        User poster = verified("jobsfilters");
        UUID remote = seed(poster, "remote", 30, job -> {
            job.setWorkplaceType(WorkplaceType.REMOTE);
            job.setWorkingHours(WorkingHours.PART_TIME);
            job.setExperienceLevel(ExperienceLevel.NONE);
        });
        UUID hybrid = seed(poster, "hybrid", 20, job -> {
            job.setWorkplaceType(WorkplaceType.HYBRID);
            job.setJobType(JobType.FREELANCE);
            job.setWorkingHours(WorkingHours.FULL_TIME);
            job.setExperienceLevel(ExperienceLevel.YEARS_5);
        });
        UUID unstated = seed(poster, "unstated", 10, job -> { });
        startFresh();

        assertThat(ids()).containsExactly(unstated, hybrid, remote);
        assertThat(ids("workplace", "REMOTE,HYBRID")).containsExactly(hybrid, remote);
        assertThat(ids("employment", "FREELANCE")).containsExactly(hybrid);
        assertThat(ids("hours", "FULL_TIME,PART_TIME")).containsExactly(hybrid, remote);
        assertThat(ids("experience", "NONE")).containsExactly(remote);
        assertThat(ids("experience", "YEARS_5")).containsExactly(hybrid);
        assertThat(ids("workplace", "REMOTE", "hours", "FULL_TIME")).isEmpty();
    }

    @Test
    void salaryPostedAndRoleNarrowTheList() throws Exception {
        User poster = verified("jobsnarrow");
        UUID fresh = seed(poster, "svez", 60, job -> job.setCategory(JobCategory.TRANSPORT_LOGISTICS));
        UUID paid = seed(poster, "placen", 2 * DAY, job -> { });
        UUID lastMonth = seed(poster, "prosli-mesec", 20 * DAY, job -> { });
        UUID old = seed(poster, "star", 40 * DAY, job -> { });
        entityManager.persist(new Salary(jobs.getReferenceById(paid), new BigDecimal("1200.00"), null, "EUR",
                SalaryPeriod.MONTH, null));
        startFresh();

        assertThat(ids()).containsExactly(fresh, paid, lastMonth, old);
        assertThat(ids("salary", "1")).containsExactly(paid);
        assertThat(ids("posted", "24h")).containsExactly(fresh);
        assertThat(ids("posted", "week")).containsExactly(fresh, paid);
        assertThat(ids("posted", "month")).containsExactly(fresh, paid, lastMonth);
        assertThat(ids("role", "transport-logistics")).containsExactly(fresh);
        list("salary", "1")
                .andExpect(jsonPath("$.content[0].salary.min").value(1200.0))
                .andExpect(jsonPath("$.content[0].salary.max").isEmpty())
                .andExpect(jsonPath("$.content[0].salary.currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].salary.period").value("MONTH"));
        list().andExpect(jsonPath("$.content[0].salary").isEmpty());
    }

    @Test
    void aTitleMatchOutranksADescriptionMatchUnlessADateSortIsAsked() throws Exception {
        User poster = verified("jobsrank");
        UUID inDescription = seed(poster, "u-opisu", 10,
                job -> job.setDescription("Restoran u centru grada trazi osobu za posao kuvar."));
        UUID inTitle = seed(poster, "u-naslovu", 60, job -> job.setTitle("Kuvar"));
        seed(poster, "bez-pogotka", 5, job -> { });
        startFresh();

        assertThat(ids("q", "kuvar")).containsExactly(inTitle, inDescription);
        assertThat(ids("q", "kuvar", "sort", "date_desc")).containsExactly(inDescription, inTitle);
        assertThat(ids("q", "kuvar", "sort", "date_asc")).containsExactly(inTitle, inDescription);
    }

    @Test
    void theSearchStemsAndFoldsDiacritics() throws Exception {
        User poster = verified("jobsstem");
        UUID programmer = seed(poster, "programer", 10, job -> job.setTitle("Programer"));
        UUID driver = seed(poster, "vozac", 20, job -> job.setTitle("Vozač kamiona"));
        UUID tagged = seed(poster, "oznacen", 30, job -> job.setKeywords(List.of("viljuškar")));
        startFresh();

        assertThat(ids("q", "programeri")).containsExactly(programmer);
        assertThat(ids("q", "vozac")).containsExactly(driver);
        assertThat(ids("q", "viljuskar")).containsExactly(tagged);
    }

    @Test
    void rowsTiedOnEveryOrderKeyStillPageTotally() throws Exception {
        User poster = verified("jobstie");
        UUID first = seed(poster, "prvi", 15, job -> job.setTitle("Kuvar"));
        UUID second = seed(poster, "drugi", 15, job -> job.setTitle("Kuvar"));
        startFresh();

        for (String q : new String[] { "", "kuvar" }) {
            String pageOne = list("page", "1", "size", "1", "q", q)
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.last").value(false))
                    .andReturn().getResponse().getContentAsString();
            String pageTwo = list("page", "2", "size", "1", "q", q)
                    .andExpect(jsonPath("$.last").value(true))
                    .andReturn().getResponse().getContentAsString();

            List<String> onOne = JsonPath.read(pageOne, "$.content[*].id");
            List<String> onTwo = JsonPath.read(pageTwo, "$.content[*].id");
            assertThat(List.of(onOne.getFirst(), onTwo.getFirst()))
                    .containsExactlyInAnyOrder(first.toString(), second.toString());
        }
    }

    @Test
    void theNextLinkCarriesTheFiltersEncoded() throws Exception {
        User poster = verified("jobslinks");
        seed(poster, "prvi-remote", 20, job -> job.setWorkplaceType(WorkplaceType.REMOTE));
        seed(poster, "drugi-remote", 10, job -> job.setWorkplaceType(WorkplaceType.REMOTE));
        startFresh();

        list("page", "1", "size", "1", "workplace", "REMOTE,HYBRID", "posted", "week")
                .andExpect(jsonPath("$._links.next.href")
                        .value("/api/jobs?page=2&size=1&workplace=REMOTE%2CHYBRID&posted=week"));
    }

    @Test
    void aSearchOverTwoHundredCharactersIs400AndABadRoleIs422() throws Exception {
        list("q", "a".repeat(201), "role", "nepostojeca")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").doesNotExist());
        list("role", "nepostojeca")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void aDraftIsNotFoundToAGuestAStrangerAndAnEditorAndServedToARecruiter() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("jobsdraftorg"));
        User editor = member(organization, "jobseditor", OrganizationRole.EDITOR);
        User recruiter = member(organization, "jobsrecruiter", OrganizationRole.RECRUITER);
        User stranger = verified("jobsstranger");
        seed(recruiter, organization, "nacrt-oglasa", JobStatus.DRAFT, 10, job -> { });
        startFresh();

        detail("nacrt-oglasa", null).andExpect(status().isNotFound());
        detail("nacrt-oglasa", stranger).andExpect(status().isNotFound());
        detail("nacrt-oglasa", editor).andExpect(status().isNotFound());
        detail("nepostojeci", recruiter).andExpect(status().isNotFound());
        detail("nacrt-oglasa", recruiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanDelete").value(true));
    }

    @Test
    void aClosedPostingServesNoWayInToAGuestAndTheWholeRecordToARecruiter() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("jobsclosedorg"));
        User recruiter = member(organization, "jobsclosedrecruiter", OrganizationRole.RECRUITER);
        seed(recruiter, organization, "zatvoren-oglas", JobStatus.CLOSED, 10, job -> {
            job.setApplicationType(ApplicationType.CONTACT_ONLY);
            job.setContactInformation(TestFixtures.contacts());
        });
        startFresh();

        Map<String, Object> guest = JsonPath.read(detail("zatvoren-oglas", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$");
        assertThat(guest)
                .containsEntry("status", "CLOSED")
                .containsEntry("contactInformation", null)
                .containsEntry("externalUrl", null)
                .containsEntry("createdForBio", null)
                .doesNotContainKeys("createdForFollowedByViewer", "viewerCanEdit", "viewerCanDelete");

        detail("zatvoren-oglas", recruiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactInformation.length()").value(2))
                .andExpect(jsonPath("$.contactInformation[0].value").value("office@example.com"))
                .andExpect(jsonPath("$.createdFor.actorType").value("org"))
                .andExpect(jsonPath("$.createdFor.handle").value("jobsclosedorg"))
                .andExpect(jsonPath("$.createdForFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(true));
    }

    @Test
    void aPersonsPostingServesNoIdentityAndItsDraftOnlyToThePoster() throws Exception {
        User poster = verified("jobsperson");
        User stranger = verified("jobspersonstranger");
        seed(poster, poster, "licni-oglas", JobStatus.OPEN, 10, job -> { });
        seed(poster, poster, "licni-nacrt", JobStatus.DRAFT, 10, job -> { });
        startFresh();

        String body = detail("licni-oglas", stranger).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> createdFor = JsonPath.read(body, "$.createdFor");
        Map<String, Object> posting = JsonPath.read(body, "$");
        assertThat(createdFor).containsOnlyKeys("actorType").containsEntry("actorType", "user");
        assertThat(posting)
                .doesNotContainKeys("createdForBio", "createdForFollowedByViewer")
                .containsEntry("viewerCanEdit", false);

        detail("licni-nacrt", stranger).andExpect(status().isNotFound());
        detail("licni-nacrt", poster)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanEdit").value(true));
    }

    @Test
    void hydrationServesTheAskedStatusesInTheSentOrderAndNeverADraft() throws Exception {
        User poster = verified("jobssaver");
        UUID open = seed(poster, poster, "sacuvan-otvoren", JobStatus.OPEN, 10, job -> { });
        UUID closed = seed(poster, poster, "sacuvan-zatvoren", JobStatus.CLOSED, 20, job -> { });
        UUID draft = seed(poster, poster, "sacuvan-nacrt", JobStatus.DRAFT, 30, job -> { });
        startFresh();
        String sent = String.join(",", closed.toString(), "job-123", UUID.randomUUID().toString(), draft.toString(),
                open.toString());

        String body = saved(sent, "OPEN,CLOSED,DRAFT").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].id");
        List<String> statuses = JsonPath.read(body, "$[*].status");
        assertThat(ids).containsExactly(closed.toString(), open.toString());
        assertThat(statuses).containsExactly("CLOSED", "OPEN");

        saved(sent, "OPEN").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(open.toString()));
        saved(sent, "DRAFT").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/jobs").param("id", sent))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    /** Sent under the context path, so links carry the prefix production serves. */
    private ResultActions list(String... pairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/jobs").contextPath("/api");
        for (int i = 0; i < pairs.length; i += 2) {
            request.param(pairs[i], pairs[i + 1]);
        }
        return mvc.perform(request);
    }

    private List<UUID> ids(String... pairs) throws Exception {
        String body = list(pairs).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }

    private ResultActions detail(String slug, User viewer) throws Exception {
        MockHttpServletRequestBuilder request = get("/jobs/" + slug);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(viewer));
        }
        return mvc.perform(request);
    }

    private ResultActions saved(String ids, String statuses) throws Exception {
        return mvc.perform(get("/jobs").param("id", ids).param("status", statuses));
    }

    private UUID seed(User poster, String slug, int minutesAgo, Consumer<Job> shape) {
        return seed(poster, poster, slug, JobStatus.OPEN, minutesAgo, shape);
    }

    /** Aged against the transaction's {@code now()}, so equal ages tie exactly. */
    private UUID seed(User poster, Actor createdFor, String slug, JobStatus status, int minutesAgo,
            Consumer<Job> shape) {
        Job job = TestFixtures.job(poster, createdFor, slug, status);
        shape.accept(job);
        UUID id = jobs.saveAndFlush(job).getId();
        entityManager.createNativeQuery("update job set created_at = now() - make_interval(mins => :minutes)"
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
