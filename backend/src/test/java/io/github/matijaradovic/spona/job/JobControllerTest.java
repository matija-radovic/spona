package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.job.dto.JobDetailDto;
import io.github.matijaradovic.spona.job.dto.JobListItemDto;
import io.github.matijaradovic.spona.job.dto.JobListQuery;
import io.github.matijaradovic.spona.job.dto.JobPosterDto;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class JobControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f3");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f4");
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05Z");
    private static final JobPosterDto PERSON = new JobPosterDto.PersonPoster(ActorType.USER);

    private final JobService jobService = mock(JobService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new JobController(jobService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFiltersReachTheServiceAsSentWithThePageAndSize() throws Exception {
        Pageable asked = PageMapper.toPageable(2, 5);
        JobListQuery sent = new JobListQuery("kuvar", "it-software", "week", "date_asc", "REMOTE,HYBRID",
                "PERMANENT", "PART_TIME", "NONE", "1");
        JobListItemDto row = new JobListItemDto(JOB_ID, "kuvar-1000", "Kuvar", PERSON, "Beograd", null, CREATED,
                List.of());
        when(jobService.list(sent, asked)).thenReturn(new PageImpl<>(List.of(row), asked, 11));

        mvc.perform(get("/jobs").param("page", "2").param("size", "5").param("q", "kuvar")
                        .param("role", "it-software").param("posted", "week").param("sort", "date_asc")
                        .param("workplace", "REMOTE,HYBRID").param("employment", "PERMANENT")
                        .param("hours", "PART_TIME").param("experience", "NONE").param("salary", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.content[0].createdFor.actorType").value("user"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11));
    }

    @Test
    void noParameterAsksForTheFirstDefaultPageWithEveryFilterAbsent() throws Exception {
        Pageable first = PageMapper.toPageable(1, PageMapper.DEFAULT_SIZE);
        when(jobService.list(any(), any())).thenReturn(Page.empty(first));

        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(jobService).list(new JobListQuery(null, null, null, null, null, null, null, null, null), first);
    }

    @Test
    void anIdParameterAnswersABareArray() throws Exception {
        SavedJobItemDto row = new SavedJobItemDto(JOB_ID, "kuvar-1000", "Kuvar", PERSON, "Beograd", null, CREATED,
                List.of(), JobStatus.CLOSED);
        when(jobService.saved("a,b", "OPEN,CLOSED")).thenReturn(List.of(row));

        mvc.perform(get("/jobs").param("id", "a,b").param("status", "OPEN,CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.length()").value(1));

        verify(jobService, never()).list(any(), any());
    }

    @Test
    void aPostingKeepsTheNullsItCarriesAndDropsTheAbsentViewerFields() throws Exception {
        when(jobService.job("kuvar-1000", null)).thenReturn(organizationPosting(null, null));

        Map<String, Object> json = read(mvc.perform(get("/jobs/kuvar-1000")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$");

        assertThat(json)
                .containsEntry("contactInformation", null)
                .containsEntry("externalUrl", null)
                .containsEntry("createdForBio", null)
                .containsEntry("workingHours", null)
                .doesNotContainKeys("createdForFollowedByViewer", "viewerCanEdit", "viewerCanDelete",
                        "applicationCount");
    }

    @Test
    void aSignedInViewerGetsTheViewerFieldsFalseIncluded() throws Exception {
        signIn();
        when(jobService.job("kuvar-1000", caller)).thenReturn(organizationPosting(false, false));

        mvc.perform(get("/jobs/kuvar-1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdFor.id").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.createdFor.handle").value("acme"))
                .andExpect(jsonPath("$.createdForFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanDelete").value(false));
    }

    @Test
    void aPersonsPostingSerialisesActorTypeAloneWithNoBioOrFollowEdge() throws Exception {
        JobDetailDto posting = new JobDetailDto.PersonPosting(JOB_ID, "kuvar-1000", "Kuvar", PERSON, "Beograd", null,
                CREATED, List.of(), JobCategory.HOSPITALITY_FOOD, JobType.PERMANENT, WorkplaceType.ONSITE, null,
                null, JobStatus.OPEN, ApplicationType.CV_DROP, null, null, "Opis", null, null);
        when(jobService.job("kuvar-1000", null)).thenReturn(posting);

        String body = mvc.perform(get("/jobs/kuvar-1000")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(read(body, "$.createdFor")).containsExactly(entry("actorType", "user"));
        assertThat(read(body, "$")).doesNotContainKeys("createdForBio", "createdForFollowedByViewer");
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    private static Map<String, Object> read(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static JobDetailDto organizationPosting(Boolean followed, Boolean canEdit) {
        JobPosterDto organization = new JobPosterDto.OrganizationPoster(ActorType.ORG, ORG_ID, "Acme", "acme", null);
        return new JobDetailDto.OrganizationPosting(JOB_ID, "kuvar-1000", "Kuvar", organization, "Beograd", null,
                CREATED, List.of("kuhinja"), JobCategory.HOSPITALITY_FOOD, JobType.PERMANENT, WorkplaceType.ONSITE,
                null, ExperienceLevel.NONE, JobStatus.CLOSED, ApplicationType.CONTACT_ONLY, null, null, "Opis",
                null, followed, canEdit, canEdit);
    }
}
