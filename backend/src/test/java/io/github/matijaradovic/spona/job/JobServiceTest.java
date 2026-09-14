package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.job.dto.JobDetailDto.OrganizationPosting;
import io.github.matijaradovic.spona.job.dto.JobDetailDto.PersonPosting;
import io.github.matijaradovic.spona.job.dto.JobListItemDto;
import io.github.matijaradovic.spona.job.dto.JobListQuery;
import io.github.matijaradovic.spona.job.dto.JobPosterDto;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class JobServiceTest {

    private static final UUID POSTER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f3");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f4");
    private static final UUID OTHER_JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f5");
    private static final String SLUG = "oglas-za-posao-1000";

    private final JobRepository jobs = mock(JobRepository.class);
    private final JobSearch search = mock(JobSearch.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final JobService service = new JobService(jobs, search, mock(ActorRepository.class),
            mock(UserRepository.class), follows, new ContentAuthority(roles));

    private final Pageable firstPage = PageMapper.toPageable(1, 20);
    private final User poster = TestFixtures.userWithId("poster", POSTER_ID);
    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final Organization organization = organization();

    @Test
    void aRoleSlugFiltersOnItsCategoryAndAnUnknownOneIs422UnderRole() {
        assertThat(JobService.filter(query(null, "it-software", null, null)).category())
                .isEqualTo(JobCategory.IT_SOFTWARE);
        assertThat(JobService.filter(query(null, "transport-logistics", null, null)).category())
                .isEqualTo(JobCategory.TRANSPORT_LOGISTICS);
        assertUnprocessable(() -> JobService.filter(query(null, "it-softwares", null, null)), "role");
    }

    @Test
    void aSearchOverTwoHundredCharactersIs400BeforeAnyRead() {
        JobListQuery tooLong = new JobListQuery("a".repeat(201), "nepostojeca", null, null, null, null, null, null,
                null);

        assertThatThrownBy(() -> service.list(tooLong, firstPage)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(search, jobs);
        assertThat(JobService.filter(query("a".repeat(200), null, null, null)).q()).hasSize(200);
    }

    @Test
    void aSearchIsTrimmedAndABlankOneIsAbsent() {
        assertThat(JobService.filter(query("  kuvar  ", null, null, null)).q()).isEqualTo("kuvar");
        assertThat(JobService.filter(query("   ", null, null, null)).q()).isNull();
    }

    @Test
    void anAbsentSortIsRelevanceWithASearchAndNewestWithout() {
        assertThat(JobService.filter(query("kuvar", null, null, null)).sort()).isEqualTo(JobFilter.Sort.RELEVANCE);
        assertThat(JobService.filter(query(null, null, null, null)).sort()).isEqualTo(JobFilter.Sort.DATE_DESC);
        assertThat(JobService.filter(query("   ", null, null, null)).sort()).isEqualTo(JobFilter.Sort.DATE_DESC);
    }

    @Test
    void anExplicitSortWinsOverRelevance() {
        assertThat(JobService.filter(query("kuvar", null, null, "date_asc")).sort())
                .isEqualTo(JobFilter.Sort.DATE_ASC);
        assertThat(JobService.filter(query("kuvar", null, null, "date_desc")).sort())
                .isEqualTo(JobFilter.Sort.DATE_DESC);
    }

    @Test
    void theListsAreCommaJoinedEnumNames() {
        JobFilter filter = JobService.filter(new JobListQuery(null, null, "week", null, "REMOTE,HYBRID", "PERMANENT",
                "PART_TIME", "NONE,YEARS_5", "1"));

        assertThat(filter.posted()).isEqualTo(JobFilter.Posted.WEEK);
        assertThat(filter.workplace()).containsExactly(WorkplaceType.REMOTE, WorkplaceType.HYBRID);
        assertThat(filter.employment()).containsExactly(JobType.PERMANENT);
        assertThat(filter.hours()).containsExactly(WorkingHours.PART_TIME);
        assertThat(filter.experience()).containsExactly(ExperienceLevel.NONE, ExperienceLevel.YEARS_5);
        assertThat(filter.salary()).isTrue();
    }

    @Test
    void aBadValueIs422UnderItsOwnParameter() {
        assertUnprocessable(() -> JobService.filter(query(null, null, "year", null)), "posted");
        assertUnprocessable(() -> JobService.filter(query(null, null, null, "relevance")), "sort");
        assertUnprocessable(() -> JobService.filter(lists("remote", null, null, null, null)), "workplace");
        assertUnprocessable(() -> JobService.filter(lists(null, "PERMANENT,", null, null, null)), "employment");
        assertUnprocessable(() -> JobService.filter(lists(null, null, "NIGHT", null, null)), "hours");
        assertUnprocessable(() -> JobService.filter(lists(null, null, null, "YEARS_4", null)), "experience");
        assertUnprocessable(() -> JobService.filter(lists(null, null, null, null, "true")), "salary");
    }

    @Test
    void theListServesTheSearchPageWithAPersonAsActorTypeAlone() {
        JobListQuery query = query("kuvar", null, null, null);
        when(search.page(JobService.filter(query), firstPage))
                .thenReturn(new PageImpl<>(List.of(job(poster, JobStatus.OPEN)), firstPage, 1));

        Page<JobListItemDto> page = service.list(query, firstPage);

        assertThat(page.getContent()).extracting(row -> row.id()).containsExactly(JOB_ID);
        assertThat(page.getContent().getFirst().createdFor()).isEqualTo(new JobPosterDto.PersonPoster(ActorType.USER));
        assertThat(page.getTotalElements()).isEqualTo(1);
        verifyNoInteractions(roles, follows);
    }

    @Test
    void anUnknownSlugIsNotFound() {
        assertThatThrownBy(() -> service.job("nepostojeci", caller)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aDraftIsNotFoundToAGuestAStrangerAndAnEditor() {
        serve(job(organization, JobStatus.DRAFT));

        assertThatThrownBy(() -> service.job(SLUG, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.job(SLUG, caller)).isInstanceOf(NotFoundException.class);
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));
        assertThatThrownBy(() -> service.job(SLUG, caller)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(follows);
    }

    @Test
    void aDraftIsServedToARecruiterAndAnOwner() {
        serve(job(organization, JobStatus.DRAFT));

        for (OrganizationRole role : List.of(OrganizationRole.RECRUITER, OrganizationRole.OWNER)) {
            when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));

            OrganizationPosting posting = (OrganizationPosting) service.job(SLUG, caller);

            assertThat(posting.status()).isEqualTo(JobStatus.DRAFT);
            assertThat(posting.viewerCanEdit()).isTrue();
            assertThat(posting.viewerCanDelete()).isTrue();
        }
    }

    @Test
    void aPersonsDraftIsServedToItsPosterAloneWithNoRoleRead() {
        serve(job(poster, JobStatus.DRAFT));

        PersonPosting posting = (PersonPosting) service.job(SLUG, poster);

        assertThat(posting.status()).isEqualTo(JobStatus.DRAFT);
        assertThat(posting.viewerCanEdit()).isTrue();
        assertThatThrownBy(() -> service.job(SLUG, caller)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(roles);
    }

    @Test
    void aClosedPostingWithholdsItsWayInFromEveryoneWhoMayNotEditIt() {
        serve(withWayIn(job(organization, JobStatus.CLOSED)));

        OrganizationPosting guest = (OrganizationPosting) service.job(SLUG, null);
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));
        OrganizationPosting editor = (OrganizationPosting) service.job(SLUG, caller);
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));
        OrganizationPosting recruiter = (OrganizationPosting) service.job(SLUG, caller);

        assertThat(guest.contactInformation()).isNull();
        assertThat(guest.externalUrl()).isNull();
        assertThat(editor.contactInformation()).isNull();
        assertThat(editor.externalUrl()).isNull();
        assertThat(recruiter.contactInformation()).isEqualTo(TestFixtures.contacts());
        assertThat(recruiter.externalUrl()).isEqualTo("https://example.com/apply");
    }

    @Test
    void anOpenPostingServesItsWayInToAGuest() {
        serve(withWayIn(job(organization, JobStatus.OPEN)));

        OrganizationPosting guest = (OrganizationPosting) service.job(SLUG, null);

        assertThat(guest.contactInformation()).isEqualTo(TestFixtures.contacts());
        assertThat(guest.externalUrl()).isEqualTo("https://example.com/apply");
    }

    @Test
    void aPersonsPostingServesActorTypeAloneAndReadsNoFollowEdge() {
        serve(job(poster, JobStatus.OPEN));

        PersonPosting posting = (PersonPosting) service.job(SLUG, caller);

        assertThat(posting.createdFor()).isEqualTo(new JobPosterDto.PersonPoster(ActorType.USER));
        assertThat(posting.viewerCanEdit()).isFalse();
        assertThat(posting.viewerCanDelete()).isFalse();
        verifyNoInteractions(follows, roles);
    }

    @Test
    void anOrganizationsPostingServesItsBioAndTheCallersEdgeForOneRoleRead() {
        organization.setDescription("Kompanija iz Beograda.");
        serve(job(organization, JobStatus.OPEN));
        when(follows.existsByFollower_IdAndFollowee_Id(CALLER_ID, ORG_ID)).thenReturn(true);
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        OrganizationPosting posting = (OrganizationPosting) service.job(SLUG, caller);

        assertThat(posting.createdFor()).isEqualTo(
                new JobPosterDto.OrganizationPoster(ActorType.ORG, ORG_ID, "Display acme", "acme", null));
        assertThat(posting.createdForBio()).isEqualTo("Kompanija iz Beograda.");
        assertThat(posting.createdForFollowedByViewer()).isTrue();
        assertThat(posting.viewerCanEdit()).isFalse();
        assertThat(posting.viewerCanDelete()).isFalse();
        verify(roles, times(1)).findLiveRole(ORG_ID, CALLER_ID);
    }

    @Test
    void aGuestGetsNoViewerFieldAndCostsNoRead() {
        serve(job(organization, JobStatus.OPEN));

        OrganizationPosting posting = (OrganizationPosting) service.job(SLUG, null);

        assertThat(posting.createdForFollowedByViewer()).isNull();
        assertThat(posting.viewerCanEdit()).isNull();
        assertThat(posting.viewerCanDelete()).isNull();
        verifyNoInteractions(roles, follows);
    }

    @Test
    void hydrationKeepsTheSentOrderDropsMalformedIdsAndNeverAsksForADraft() {
        Job open = job(organization, JobStatus.OPEN, JOB_ID);
        Job closed = job(poster, JobStatus.CLOSED, OTHER_JOB_ID);
        when(jobs.findAllInOrder(List.of(OTHER_JOB_ID, JOB_ID), EnumSet.of(JobStatus.OPEN, JobStatus.CLOSED)))
                .thenReturn(List.of(closed, open));

        List<SavedJobItemDto> rows = service.saved(
                OTHER_JOB_ID + ",job-123," + JOB_ID + "," + OTHER_JOB_ID, "OPEN,CLOSED,DRAFT");

        assertThat(rows).extracting(row -> row.id()).containsExactly(OTHER_JOB_ID, JOB_ID);
        assertThat(rows).extracting(row -> row.status()).containsExactly(JobStatus.CLOSED, JobStatus.OPEN);
        verifyNoInteractions(roles, follows);
    }

    @Test
    void hydrationAskingOnlyForADraftAsksForNoStatus() {
        service.saved(JOB_ID.toString(), "DRAFT");

        verify(jobs).findAllInOrder(List.of(JOB_ID), Set.of());
    }

    @Test
    void aMissingOrUnknownStatusIs422UnderStatus() {
        assertUnprocessable(() -> service.saved(JOB_ID.toString(), null), "status");
        assertUnprocessable(() -> service.saved(JOB_ID.toString(), "OPEN,ARCHIVED"), "status");
        verify(jobs, never()).findAllInOrder(any(), any());
    }

    private void serve(Job job) {
        when(jobs.findBySlugWithCreatedFor(SLUG)).thenReturn(Optional.of(job));
    }

    private static JobListQuery query(String q, String role, String posted, String sort) {
        return new JobListQuery(q, role, posted, sort, null, null, null, null, null);
    }

    private static JobListQuery lists(String workplace, String employment, String hours, String experience,
            String salary) {
        return new JobListQuery(null, null, null, null, workplace, employment, hours, experience, salary);
    }

    private static void assertUnprocessable(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(UnprocessableException.class,
                ex -> assertThat(ex.getErrors()).containsOnlyKeys(field));
    }

    private static Job job(Actor createdFor, JobStatus status) {
        return job(createdFor, status, JOB_ID);
    }

    private static Job job(Actor createdFor, JobStatus status, UUID id) {
        Job job = TestFixtures.job(TestFixtures.user("writer"), createdFor, SLUG, status);
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        return job;
    }

    /** Both ways in at once, which no row may hold, so one assertion covers both fields. */
    private static Job withWayIn(Job job) {
        job.setApplicationType(ApplicationType.CONTACT_ONLY);
        job.setContactInformation(TestFixtures.contacts());
        job.setExternalUrl("https://example.com/apply");
        return job;
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
