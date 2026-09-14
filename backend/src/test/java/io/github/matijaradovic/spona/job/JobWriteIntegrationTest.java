package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
class JobWriteIntegrationTest {

    private static final String DESCRIPTION = "Opis radnog mesta, dovoljno dugacak da prodje svaku proveru duzine.";

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
    void aPersonCreatesAnOpenPostingThatThePublicDetailServes() throws Exception {
        User poster = verified("jobwriter");

        String body = mvc.perform(as(post("/jobs"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content(creation(poster.getId(), "OPEN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Kuvar u Čačku"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.salary.max").value(1500.5))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.slug");
        assertThat(slug).matches("kuvar-u-cacku-\\d{4}");
        startFresh();

        mvc.perform(get("/jobs/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdFor.actorType").value("user"))
                .andExpect(jsonPath("$.createdFor.id").doesNotExist())
                .andExpect(jsonPath("$.contactInformation[0].value").value("posao@primer.rs"))
                .andExpect(jsonPath("$.keywords[0]").value("kuhinja"))
                .andExpect(jsonPath("$.salary.min").value(1200));
        assertThat(entityManager.createNativeQuery("select created_by_id from job where slug = :slug")
                .setParameter("slug", slug).getSingleResult()).isEqualTo(poster.getId());
    }

    @Test
    void anOrganizationPublishesThroughARecruiterAndNeverThroughAnEditorOrAGuest() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("jobwriteorg"));
        User recruiter = member(organization, "jobrecruiter", OrganizationRole.RECRUITER);
        User editor = member(organization, "jobeditor", OrganizationRole.EDITOR);

        mvc.perform(post("/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(creation(organization.getId(), "OPEN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(post("/jobs"), editor).contentType(MediaType.APPLICATION_JSON)
                        .content(creation(organization.getId(), "OPEN")))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/jobs"), recruiter).contentType(MediaType.APPLICATION_JSON)
                        .content(creation(organization.getId(), "OPEN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdFor.handle").value("jobwriteorg"))
                .andExpect(jsonPath("$.createdForFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanDelete").value(true));
    }

    @Test
    void aDraftIsSavedUnfinishedAndPublishingItNamesWhatIsMissing() throws Exception {
        User poster = verified("jobdrafter");

        String body = mvc.perform(as(post("/jobs"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdForId":"%s","status":"DRAFT","applicationType":"CONTACT_ONLY"}
                                """.formatted(poster.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.contactInformation").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.slug")).matches(JobService.SLUG_FALLBACK + "-\\d{4}");
        startFresh();

        mvc.perform(as(put("/jobs/" + id + "/status"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.contactInformation").exists());
        mvc.perform(as(put("/jobs/" + id), poster).contentType(MediaType.APPLICATION_JSON).content(update("CONTACT_ONLY")))
                .andExpect(status().isOk());
        mvc.perform(as(put("/jobs/" + id + "/status"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(as(put("/jobs/" + id + "/status"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anUpdateWritesNeitherStatusNorPublisherAndKeepsThePublishedWayIn() throws Exception {
        User poster = verified("jobupdater");
        User other = verified("jobother");
        UUID id = seed(poster, JobStatus.OPEN);

        mvc.perform(as(put("/jobs/" + id), poster).contentType(MediaType.APPLICATION_JSON).content(update("CONTACT_ONLY")))
                .andExpect(status().isConflict());
        mvc.perform(as(put("/jobs/" + id), poster).contentType(MediaType.APPLICATION_JSON)
                        .content(with("\"status\":\"CLOSED\",\"createdForId\":\"" + other.getId() + "\"",
                                update("CV_DROP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.title").value("Kuvar u Čačku"));
        startFresh();

        assertThat(jobs.findWithCreatedFor(id).orElseThrow().getCreatedFor().getId()).isEqualTo(poster.getId());
    }

    @Test
    void everyWriteStampsUpdatedAtASalaryOnlyEditAndAStatusChangeIncluded() throws Exception {
        User poster = verified("jobstamper");
        UUID id = seed(poster, JobStatus.OPEN);
        String salaryOnly = """
                {"title":"Oglas za posao","description":"%s","keywords":[],"category":"IT_SOFTWARE",
                 "jobType":"PERMANENT","workplaceType":"ONSITE","location":"Beograd",
                 "salary":{"min":900,"currency":"EUR","period":"MONTH"},"applicationType":"CV_DROP"}
                """.formatted(DESCRIPTION);

        age(id);
        mvc.perform(as(put("/jobs/" + id), poster).contentType(MediaType.APPLICATION_JSON).content(salaryOnly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary.min").value(900));
        assertThat(stamped(id)).isTrue();
        assertThat(count("salary", "job_id", id)).isEqualTo(1);

        age(id);
        mvc.perform(as(put("/jobs/" + id + "/status"), poster).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(stamped(id)).isTrue();
    }

    @Test
    void aStrangerMeets404OnADraftAnd403ElsewhereAndADeleteTakesTheSalaryAndApplications() throws Exception {
        User poster = verified("jobdeleter");
        User stranger = verified("jobstranger");
        UUID draft = seed(poster, JobStatus.DRAFT);
        UUID open = seed(poster, JobStatus.OPEN);
        entityManager.persist(new Salary(jobs.getReferenceById(open), new BigDecimal("1000.00"), null, "EUR",
                SalaryPeriod.MONTH, null));
        entityManager.flush();
        entityManager.createNativeQuery("insert into job_application (job_id, cv_object_key, created_by_id)"
                + " values (:job, 'cv/prijava.pdf', :user)")
                .setParameter("job", open)
                .setParameter("user", stranger.getId())
                .executeUpdate();
        startFresh();

        mvc.perform(as(put("/jobs/" + draft + "/status"), stranger).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/jobs/" + draft), stranger)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/jobs/" + open), stranger)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/jobs/" + open), poster)).andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(count("job", "id", open)).isZero();
        assertThat(count("salary", "job_id", open)).isZero();
        assertThat(count("job_application", "job_id", open)).isZero();
        assertThat(count("job", "id", draft)).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }

    private static String creation(UUID createdForId, String status) {
        return with("\"createdForId\":\"%s\",\"status\":\"%s\"".formatted(createdForId, status), update("CONTACT_ONLY"));
    }

    /** The given members prepended to a JSON object. */
    private static String with(String members, String json) {
        return "{" + members + "," + json.strip().substring(1);
    }

    private static String update(String applicationType) {
        return """
                {"title":"  Kuvar u Čačku ","description":"%s","keywords":["kuhinja"],
                 "category":"HOSPITALITY_FOOD","jobType":"PERMANENT","workplaceType":"ONSITE","location":"Čačak",
                 "salary":{"min":1200,"max":1500.5,"currency":"EUR","period":"MONTH"},"applicationType":"%s",
                 "contactInformation":[{"type":"email","description":"","value":"posao@primer.rs"}]}
                """.formatted(DESCRIPTION, applicationType);
    }

    private UUID seed(User poster, JobStatus status) {
        return jobs.saveAndFlush(TestFixtures.job(poster, poster, "oglas-" + UUID.randomUUID(), status)).getId();
    }

    private void age(UUID id) {
        entityManager.flush();
        entityManager.createNativeQuery("update job set updated_at = timestamptz '2020-01-01 00:00:00Z' where id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
    }

    private boolean stamped(UUID id) {
        entityManager.flush();
        return (Boolean) entityManager
                .createNativeQuery("select updated_at > timestamptz '2021-01-01 00:00:00Z' from job where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    private long count(String table, String column, UUID id) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from " + table + " where " + column + " = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
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
