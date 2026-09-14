package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.job.application.JobApplicationRepository;
import io.github.matijaradovic.spona.job.dto.CreateJobRequest;
import io.github.matijaradovic.spona.job.dto.JobDetailDto;
import io.github.matijaradovic.spona.job.dto.JobDetailDto.OrganizationPosting;
import io.github.matijaradovic.spona.job.dto.JobDetailDto.PersonPosting;
import io.github.matijaradovic.spona.job.dto.SalaryRequest;
import io.github.matijaradovic.spona.job.dto.UpdateJobRequest;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class JobWriteServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e3");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e4");
    private static final String DESCRIPTION = "Opis radnog mesta, dovoljno dugacak da prodje svaku proveru duzine.";

    private final JobRepository jobs = mock(JobRepository.class);
    private final ActorRepository actors = mock(ActorRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final JobService service = new JobService(jobs, mock(JobSearch.class), actors, users, follows,
            new ContentAuthority(roles), mock(PrivacyService.class), applications);

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final Organization organization = organization();

    @BeforeEach
    void stubWrites() {
        when(users.getReferenceById(CALLER_ID)).thenReturn(caller);
        when(actors.findById(CALLER_ID)).thenReturn(Optional.of(caller));
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(jobs.saveAndFlush(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aPersonPublishesAsThemselvesAndTheSlugIsTheTitlePlusANumber() {
        JobDetailDto dto = service.create(caller, create(CALLER_ID, JobStatus.OPEN));

        assertThat(dto).isInstanceOfSatisfying(PersonPosting.class, posting -> {
            assertThat(posting.slug()).matches("kuvar-u-cacku-\\d{4}");
            assertThat(posting.title()).isEqualTo("Kuvar u Čačku");
            assertThat(posting.status()).isEqualTo(JobStatus.OPEN);
            assertThat(posting.viewerCanEdit()).isTrue();
            assertThat(posting.viewerCanDelete()).isTrue();
        });
        verify(jobs).saveAndFlush(any(Job.class));
        verifyNoInteractions(roles, follows);
    }

    @Test
    void aCreateServesNoApplicationsWithNoQueryAndTheOtherTwoWritesCount() {
        when(applications.countByJob_Id(JOB_ID)).thenReturn(3L);

        JobDetailDto created = service.create(caller, create(CALLER_ID, JobStatus.OPEN));
        assertThat(((PersonPosting) created).applicationCount()).isZero();
        verifyNoInteractions(applications);

        serve(caller, JobStatus.OPEN);
        JobDetailDto updated = service.update(JOB_ID, caller, update("Kuvar", ApplicationType.CV_DROP, null, null));
        JobDetailDto closed = service.setStatus(JOB_ID, caller, JobStatus.CLOSED);

        assertThat(((PersonPosting) updated).applicationCount()).isEqualTo(3L);
        assertThat(((PersonPosting) closed).applicationCount()).isEqualTo(3L);
    }

    @Test
    void aClosedCreateIs422UnderStatusBeforeAnyRead() {
        assertUnprocessable(() -> service.create(caller, create(CALLER_ID, JobStatus.CLOSED)), "status");
        verifyNoInteractions(actors, jobs);
    }

    @Test
    void anOrganizationPublishesThroughARoleThatManagesPostingsAndNoOther() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));
        when(follows.existsByFollower_IdAndFollowee_Id(CALLER_ID, ORG_ID)).thenReturn(true);

        assertThat(service.create(caller, create(ORG_ID, JobStatus.OPEN)))
                .isInstanceOfSatisfying(OrganizationPosting.class, posting -> {
                    assertThat(posting.createdForFollowedByViewer()).isTrue();
                    assertThat(posting.viewerCanEdit()).isTrue();
                });

        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));
        assertThatThrownBy(() -> service.create(caller, create(ORG_ID, JobStatus.OPEN)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anUnknownActorIs404() {
        UUID nobody = UUID.randomUUID();
        when(actors.findById(nobody)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(caller, create(nobody, JobStatus.DRAFT)))
                .isInstanceOf(NotFoundException.class);
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void anOpenCreateNamesEveryMissingFieldAndADraftIsSavedUnfinished() {
        CreateJobRequest bare = new CreateJobRequest(CALLER_ID, "", "", List.of(), null, null, null, null, null, "",
                null, JobStatus.OPEN, ApplicationType.CONTACT_ONLY, List.of(), null);

        assertThatThrownBy(() -> service.create(caller, bare)).isInstanceOfSatisfying(UnprocessableException.class,
                ex -> assertThat(ex.getErrors()).containsOnlyKeys("title", "description", "category", "jobType",
                        "workplaceType", "location", "contactInformation"));

        CreateJobRequest draft = new CreateJobRequest(CALLER_ID, "", "", null, null, null, null, null, null, null,
                null, JobStatus.DRAFT, ApplicationType.EXTERNAL_URL, null, null);
        assertThat(service.create(caller, draft)).isInstanceOfSatisfying(PersonPosting.class, posting -> {
            assertThat(posting.slug()).matches(JobService.SLUG_FALLBACK + "-\\d{4}");
            assertThat(posting.externalUrl()).isEmpty();
            assertThat(posting.contactInformation()).isNull();
            assertThat(posting.keywords()).isEmpty();
        });
    }

    @Test
    void aTakenSlugIsRedrawnWithOneMoreDigit() {
        when(jobs.existsBySlug(anyString())).thenReturn(true, false);

        PersonPosting posting = (PersonPosting) service.create(caller, create(CALLER_ID, JobStatus.DRAFT));
        assertThat(posting.slug()).matches("kuvar-u-cacku-\\d{5}");
    }

    @Test
    void theSlugBaseIsAsciiWordsWithAFallbackAndABound() {
        assertThat(JobService.slugBase("  Đak/Šef smene :: Žabalj 2026!  ")).isEqualTo("djak-sef-smene-zabalj-2026");
        assertThat(JobService.slugBase("Кувар")).isEqualTo(JobService.SLUG_FALLBACK);
        assertThat(JobService.slugBase("ab ".repeat(60))).hasSizeLessThanOrEqualTo(JobService.SLUG_BASE_MAX)
                .doesNotEndWith("-");
    }

    @Test
    void textIsTrimmedAndBoundedBlankOrWithinAndOnlyTheValueUnderTheTypeIsKept() {
        JobForm form = JobForm.of(update(" Kuvar ", ApplicationType.CV_DROP, TestFixtures.contacts(), "https://x.rs"));
        assertThat(form.title()).isEqualTo("Kuvar");
        assertThat(form.contactInformation()).isNull();
        assertThat(form.externalUrl()).isNull();

        assertUnprocessable(() -> JobForm.of(update("ab", ApplicationType.CV_DROP, null, null)), "title");
        assertThat(JobForm.of(update("   ", ApplicationType.CV_DROP, null, null)).title()).isEmpty();
        assertUnprocessable(() -> JobForm.of(update("x".repeat(121), ApplicationType.CV_DROP, null, null)), "title");
        assertUnprocessable(() -> JobForm.of(update("Kuvar", ApplicationType.EXTERNAL_URL, null, "javascript:alert(1)")),
                "externalUrl");
        assertUnprocessable(() -> JobForm.of(update("Kuvar", ApplicationType.EXTERNAL_URL, null,
                "https://x.rs/" + "a".repeat(300))), "externalUrl");
    }

    @Test
    void keywordsAreAtMostTenAndEachOneToThirtyTwoCharacters() {
        assertThat(JobForm.of(keywords(List.of(" java ", "spring"))).keywords()).containsExactly("java", "spring");
        assertUnprocessable(() -> JobForm.of(keywords(Collections.nCopies(11, "java"))), "keywords");
        assertUnprocessable(() -> JobForm.of(keywords(List.of("java", "  "))), "keywords");
        assertUnprocessable(() -> JobForm.of(keywords(List.of("x".repeat(33)))), "keywords");
    }

    @Test
    void aSalaryMaximumBelowItsMinimumIs422UnderSalaryMax() {
        SalaryRequest inverted = new SalaryRequest(new BigDecimal("2000"), new BigDecimal("1000"), "EUR",
                SalaryPeriod.MONTH);
        UpdateJobRequest request = new UpdateJobRequest("Kuvar", DESCRIPTION, List.of(), null, null, null, null, null,
                "Beograd", inverted, ApplicationType.CV_DROP, null, null);

        assertUnprocessable(() -> JobForm.of(request), "salary.max");
    }

    @Test
    void aPublishedPostingKeepsItsWayInWhileADraftMayChangeIt() {
        Job open = serve(caller, JobStatus.OPEN);

        assertThatThrownBy(() -> service.update(JOB_ID, caller,
                update("Kuvar", ApplicationType.EXTERNAL_URL, null, "https://x.rs/prijava")))
                .isInstanceOf(ConflictException.class);
        assertThat(open.getApplicationType()).isEqualTo(ApplicationType.CV_DROP);

        Job draft = serve(caller, JobStatus.DRAFT);
        service.update(JOB_ID, caller, update("Kuvar", ApplicationType.EXTERNAL_URL, null, "https://x.rs/prijava"));
        assertThat(draft.getApplicationType()).isEqualTo(ApplicationType.EXTERNAL_URL);
        assertThat(draft.getExternalUrl()).isEqualTo("https://x.rs/prijava");
    }

    @Test
    void aPublishedPostingMayNotBeEditedOutOfPublishable() {
        Job open = serve(caller, JobStatus.OPEN);
        open.setApplicationType(ApplicationType.CONTACT_ONLY);
        open.setContactInformation(TestFixtures.contacts());

        assertUnprocessable(() -> service.update(JOB_ID, caller,
                update("Kuvar", ApplicationType.CONTACT_ONLY, List.of(), null)), "contactInformation");

        List<ContactEntry> one = List.of(TestFixtures.contacts().getFirst());
        service.update(JOB_ID, caller, update("Kuvar", ApplicationType.CONTACT_ONLY, one, null));
        assertThat(open.getContactInformation()).isEqualTo(one);
    }

    @Test
    void anUpdateStampsTheRowAndSetsTheSalaryInPlace() {
        Job open = serve(caller, JobStatus.OPEN);
        Salary held = new Salary(open, new BigDecimal("1000.00"), null, "EUR", SalaryPeriod.MONTH, "po dogovoru");
        open.setSalary(held);
        SalaryRequest raised = new SalaryRequest(new BigDecimal("1500.5"), new BigDecimal("2000"), "RSD",
                SalaryPeriod.HOUR);

        JobDetailDto dto = service.update(JOB_ID, caller, new UpdateJobRequest("Kuvar", DESCRIPTION, List.of(),
                JobCategory.IT_SOFTWARE, JobType.PERMANENT, WorkplaceType.ONSITE, null, null, "Beograd", raised,
                ApplicationType.CV_DROP, null, null));

        assertThat(open.getSalary()).isSameAs(held);
        assertThat(held.getMin()).isEqualByComparingTo("1500.50");
        assertThat(held.getMax()).isEqualByComparingTo("2000");
        assertThat(held.getCurrency()).isEqualTo("RSD");
        assertThat(held.getDisplayText()).isEqualTo("po dogovoru");
        assertThat(open.getUpdatedAt()).isNotNull();
        assertThat(((PersonPosting) dto).salary().min()).isEqualByComparingTo("1500.50");
    }

    @Test
    void aSalaryLeftOutIsRemoved() {
        Job draft = serve(caller, JobStatus.DRAFT);
        draft.setSalary(new Salary(draft, BigDecimal.ONE, null, "EUR", SalaryPeriod.MONTH, null));

        service.update(JOB_ID, caller, update("Kuvar", ApplicationType.CV_DROP, null, null));

        assertThat(draft.getSalary()).isNull();
    }

    @Test
    void aDraftIs404ToWhoeverMayNotEditItAndAPublishedPostingIs403() {
        User stranger = TestFixtures.userWithId("stranger", UUID.randomUUID());
        serve(caller, JobStatus.DRAFT);

        assertThatThrownBy(() -> service.update(JOB_ID, stranger, update("Kuvar", ApplicationType.CV_DROP, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setStatus(JOB_ID, stranger, JobStatus.OPEN))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(JOB_ID, stranger)).isInstanceOf(NotFoundException.class);

        serve(caller, JobStatus.OPEN);
        assertThatThrownBy(() -> service.delete(JOB_ID, stranger)).isInstanceOf(ForbiddenException.class);
        verify(jobs, never()).delete(any());
    }

    @Test
    void anEditorManagesNoPostingOfTheOrganizationAndARecruiterManagesThemAll() {
        Job open = serve(organization, JobStatus.OPEN);
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        assertThatThrownBy(() -> service.delete(JOB_ID, caller)).isInstanceOf(ForbiddenException.class);

        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));
        service.delete(JOB_ID, caller);
        verify(jobs).delete(open);
    }

    @Test
    void theLadderIsDraftToOpenAndOpenToAndFromClosed() {
        for (JobStatus from : JobStatus.values()) {
            for (JobStatus to : JobStatus.values()) {
                Job job = serve(caller, from);
                boolean allowed = from == JobStatus.DRAFT && to == JobStatus.OPEN
                        || from == JobStatus.OPEN && to == JobStatus.CLOSED
                        || from == JobStatus.CLOSED && to == JobStatus.OPEN;
                if (allowed) {
                    assertThat(((PersonPosting) service.setStatus(JOB_ID, caller, to)).status()).isEqualTo(to);
                    assertThat(job.getStatus()).isEqualTo(to);
                } else {
                    assertThatThrownBy(() -> service.setStatus(JOB_ID, caller, to))
                            .isInstanceOf(ConflictException.class);
                    assertThat(job.getStatus()).isEqualTo(from);
                }
            }
        }
    }

    @Test
    void publishingAnUnfinishedDraftNamesWhatIsMissing() {
        Job draft = serve(caller, JobStatus.DRAFT);
        draft.setCategory(null);
        draft.setTitle("");

        assertThatThrownBy(() -> service.setStatus(JOB_ID, caller, JobStatus.OPEN))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsOnlyKeys("title", "category"));
        assertThat(draft.getStatus()).isEqualTo(JobStatus.DRAFT);
    }

    private Job serve(Actor createdFor, JobStatus status) {
        Job job = TestFixtures.job(caller, createdFor, "oglas-za-posao-1000", status);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        ReflectionTestUtils.setField(job, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        when(jobs.findWithCreatedFor(JOB_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private static CreateJobRequest create(UUID createdForId, JobStatus status) {
        return new CreateJobRequest(createdForId, "  Kuvar u Čačku ", DESCRIPTION, List.of("kuhinja"),
                JobCategory.HOSPITALITY_FOOD, JobType.PERMANENT, WorkplaceType.ONSITE, null, null, "Čačak", null,
                status, ApplicationType.CV_DROP, null, null);
    }

    /** Publishable whatever the type, so each test changes one thing. */
    private static UpdateJobRequest update(String title, ApplicationType type, List<ContactEntry> contacts,
            String externalUrl) {
        return new UpdateJobRequest(title, DESCRIPTION, List.of(), JobCategory.IT_SOFTWARE, JobType.PERMANENT,
                WorkplaceType.ONSITE, null, null, "Beograd", null, type, contacts, externalUrl);
    }

    private static UpdateJobRequest keywords(List<String> keywords) {
        return new UpdateJobRequest("Kuvar", DESCRIPTION, keywords, null, null, null, null, null, "Beograd", null,
                ApplicationType.CV_DROP, null, null);
    }

    private static void assertUnprocessable(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(UnprocessableException.class,
                ex -> assertThat(ex.getErrors()).containsOnlyKeys(field));
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
