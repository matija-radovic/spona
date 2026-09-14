package io.github.matijaradovic.spona.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.job.JobRepository;
import io.github.matijaradovic.spona.job.JobStatus;
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

/**
 * Real Postgres and the compose MinIO, so the stack has to be up. The presigned link is fetched
 * with no credentials, because that is what a browser is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class JobApplicationIntegrationTest {

    private static final byte[] CV = "%PDF-1.7 zivotopis".getBytes(StandardCharsets.UTF_8);

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
    private JobApplicationRepository applications;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtService jwtService;

    @Test
    void aSubmitIsAnsweredWithTheRowAndTheInboxServesALinkThatFetchesTheSameBytes() throws Exception {
        User poster = verified("applyposter");
        User applicant = verified("applyapplicant");
        UUID jobId = seed(poster, poster, "apply-inbox-1000", JobStatus.OPEN);

        String created = submit(jobId, applicant, CV).andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicant.handle").value("applyapplicant"))
                .andExpect(jsonPath("$.applicant.actorType").value("user"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(created, "$.cvUrl")).contains("X-Amz-Signature=");
        assertThat(JsonPath.<Map<String, Object>>read(created, "$")).containsOnlyKeys("id", "applicant", "cvUrl",
                "submittedAt");

        startFresh();
        String inbox = body(inbox(jobId, poster));
        List<String> links = JsonPath.read(inbox, "$.content[*].cvUrl");

        assertThat(JsonPath.<Integer>read(inbox, "$.totalElements")).isEqualTo(1);
        assertThat(fetch(links.get(0)).body()).isEqualTo(CV);
    }

    @Test
    void theStoredValueIsAKeyAndNotTheServedLink() throws Exception {
        User poster = verified("applykeyposter");
        User applicant = verified("applykeyapplicant");
        UUID jobId = seed(poster, poster, "apply-key-1000", JobStatus.OPEN);

        submit(jobId, applicant, CV).andExpect(status().isCreated());
        startFresh();

        JobApplication row = applications.findAll().stream()
                .filter(candidate -> candidate.getJob().getId().equals(jobId))
                .findFirst()
                .orElseThrow();
        assertThat(row.getCvObjectKey()).doesNotStartWith("http").endsWith(".pdf");
    }

    @Test
    void aGuestsSubmitIs401AndANonPdfIs422UnderCv() throws Exception {
        User poster = verified("applyguestposter");
        User applicant = verified("applyguestapplicant");
        UUID jobId = seed(poster, poster, "apply-guest-1000", JobStatus.OPEN);

        submit(jobId, null, CV).andExpect(status().isUnauthorized());
        submit(jobId, applicant, "GIF89a".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.cv").isNotEmpty());
    }

    @Test
    void aDraftIs409ToItsPosterAnd404ToEveryoneElseAndAClosedPostingIs409ToBoth() throws Exception {
        User poster = verified("applystatusposter");
        User stranger = verified("applystatusstranger");
        UUID draft = seed(poster, poster, "apply-draft-1000", JobStatus.DRAFT);
        UUID closed = seed(poster, poster, "apply-closed-1000", JobStatus.CLOSED);

        submit(draft, poster, CV).andExpect(status().isConflict());
        submit(draft, stranger, CV).andExpect(status().isNotFound());
        submit(closed, poster, CV).andExpect(status().isConflict());
        submit(closed, stranger, CV).andExpect(status().isConflict());
    }

    @Test
    void anOpenPostingThatIsNotCvDropIs409() throws Exception {
        User poster = verified("applytypeposter");
        User applicant = verified("applytypeapplicant");
        Job job = TestFixtures.job(poster, poster, "apply-type-1000", JobStatus.OPEN);
        job.setApplicationType(ApplicationType.EXTERNAL_URL);
        job.setExternalUrl("https://example.com/apply");
        UUID jobId = jobs.saveAndFlush(job).getId();

        submit(jobId, applicant, CV).andExpect(status().isConflict());
    }

    @Test
    void anEditorsInboxIs403AndARecruitersIsServed() throws Exception {
        Organization organization = organizations.saveAndFlush(TestFixtures.organization("applyinboxorg"));
        User recruiter = member(organization, "applyrecruiter", OrganizationRole.RECRUITER);
        User editor = member(organization, "applyeditor", OrganizationRole.EDITOR);
        User stranger = verified("applyinboxstranger");
        UUID jobId = seed(recruiter, organization, "apply-roles-1000", JobStatus.OPEN);

        inbox(jobId, editor).andExpect(status().isForbidden());
        inbox(jobId, stranger).andExpect(status().isForbidden());
        inbox(jobId, null).andExpect(status().isUnauthorized());
        inbox(jobId, recruiter).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theDetailCarriesApplicationCountForAnEditorAndOmitsItForEveryoneElse() throws Exception {
        User poster = verified("applycountposter");
        User applicant = verified("applycountapplicant");
        UUID jobId = seed(poster, poster, "apply-count-1000", JobStatus.OPEN);
        submit(jobId, applicant, CV).andExpect(status().isCreated());
        startFresh();

        String own = body(detail("apply-count-1000", poster));
        String stranger = body(detail("apply-count-1000", applicant));
        String guest = body(detail("apply-count-1000", null));

        assertThat(JsonPath.<Integer>read(own, "$.applicationCount")).isEqualTo(1);
        assertThat(JsonPath.<Map<String, Object>>read(stranger, "$")).doesNotContainKey("applicationCount");
        assertThat(JsonPath.<Map<String, Object>>read(guest, "$")).doesNotContainKey("applicationCount");
    }

    private ResultActions submit(UUID jobId, User caller, byte[] bytes) throws Exception {
        return mvc.perform(token(multipart("/api/jobs/" + jobId + "/applications")
                .file(new MockMultipartFile("cv", "zivotopis.pdf", "application/pdf", bytes))
                .contextPath("/api"), caller));
    }

    private ResultActions inbox(UUID jobId, User caller) throws Exception {
        return mvc.perform(token(get("/api/jobs/" + jobId + "/applications").contextPath("/api"), caller));
    }

    private ResultActions detail(String slug, User caller) throws Exception {
        return mvc.perform(token(get("/api/jobs/" + slug).contextPath("/api"), caller));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B token(B request, User caller) {
        return caller == null
                ? request
                : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(caller));
    }

    private static String body(ResultActions response) throws Exception {
        return response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private UUID seed(User poster, Actor createdFor, String slug, JobStatus status) {
        return jobs.saveAndFlush(TestFixtures.job(poster, createdFor, slug, status)).getId();
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

    private static HttpResponse<byte[]> fetch(String url) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
