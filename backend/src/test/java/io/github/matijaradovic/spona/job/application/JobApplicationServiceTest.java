package io.github.matijaradovic.spona.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.job.JobRepository;
import io.github.matijaradovic.spona.job.JobService;
import io.github.matijaradovic.spona.job.JobStatus;
import io.github.matijaradovic.spona.job.dto.JobApplicationDto;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.storage.StorageService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class JobApplicationServiceTest {

    private static final UUID POSTER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c4");
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c5");
    private static final String KEY = "6f1e2c3d-4a5b.pdf";
    private static final byte[] PDF = "%PDF-1.7 nesto".getBytes(StandardCharsets.UTF_8);

    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final ContentAuthority authority = new ContentAuthority(roles);

    /** A real one, so the inbox is gated by the same {@code editable} the writes are. */
    private final JobService jobService = new JobService(jobs, null, mock(ActorRepository.class), users,
            mock(FollowRepository.class), authority, mock(PrivacyService.class), applications);
    private final JobApplicationService service = new JobApplicationService(applications, jobs, jobService, users,
            storage, authority);

    private final Pageable firstPage = PageMapper.toPageable(1, 10);
    private final User poster = TestFixtures.userWithId("poster", POSTER_ID);
    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final Organization organization = organization();

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aMissingAnEmptyAndANonPdfFileAre422UnderCvBeforeAnyRead() {
        assertUnprocessable(() -> service.submit(JOB_ID, caller, null), "cv");
        assertUnprocessable(() -> service.submit(JOB_ID, caller, file(new byte[0])), "cv");
        assertUnprocessable(() -> service.submit(JOB_ID, caller, file("%PD".getBytes(StandardCharsets.UTF_8))), "cv");
        assertUnprocessable(() -> service.submit(JOB_ID, caller, file("GIF89a nesto".getBytes(StandardCharsets.UTF_8))),
                "cv");

        verifyNoInteractions(jobs, storage, applications, roles);
    }

    @Test
    void anUnknownPostingIsNotFoundAndStoresNothing() {
        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(storage, applications);
    }

    @Test
    void aDraftIsConflictToARecruiterAndNotFoundToAStrangerForOneRoleRead() {
        serve(job(organization, JobStatus.DRAFT));
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(ConflictException.class);

        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(NotFoundException.class);

        verify(roles, times(2)).findLiveRole(ORG_ID, CALLER_ID);
        verifyNoInteractions(storage, applications);
    }

    @Test
    void aClosedPostingIsConflictToItsEditorAsWell() {
        serve(job(organization, JobStatus.CLOSED));
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(ConflictException.class);

        verifyNoInteractions(storage, applications);
    }

    @Test
    void anOpenPostingThatIsNotCvDropIsConflict() {
        Job job = job(organization, JobStatus.OPEN);
        job.setApplicationType(ApplicationType.CONTACT_ONLY);
        serve(job);

        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(ConflictException.class);

        job.setApplicationType(ApplicationType.EXTERNAL_URL);
        assertThatThrownBy(() -> service.submit(JOB_ID, caller, file(PDF))).isInstanceOf(ConflictException.class);

        verifyNoInteractions(storage, applications, roles);
    }

    @Test
    void anAcceptedFileIsStoredAsAPdfWhateverItDeclaredAndAnsweredWithTheRow() {
        Job job = job(organization, JobStatus.OPEN);
        serve(job);
        stubStore(job);

        JobApplicationDto row = service.submit(JOB_ID, caller,
                new MockMultipartFile("cv", "zivotopis.txt", "text/plain", PDF));

        verify(storage).putCv(PDF, "application/pdf");
        assertThat(row.id()).isEqualTo(ROW_ID);
        assertThat(row.cvUrl()).isEqualTo("https://minio/signed");
        assertThat(row.applicant().actorType()).isEqualTo(ActorType.USER);
        assertThat(row.applicant().handle()).isEqualTo("caller");
    }

    @Test
    void aRollbackDeletesTheStoredKey() {
        Job job = job(organization, JobStatus.OPEN);
        serve(job);
        stubStore(job);
        TransactionSynchronizationManager.initSynchronization();

        service.submit(JOB_ID, caller, file(PDF));
        rollback();

        verify(storage).deleteCv(KEY);
    }

    @Test
    void aCommitKeepsTheStoredKey() {
        Job job = job(organization, JobStatus.OPEN);
        serve(job);
        stubStore(job);
        TransactionSynchronizationManager.initSynchronization();

        service.submit(JOB_ID, caller, file(PDF));
        commit();

        verify(storage, never()).deleteCv(any());
    }

    @Test
    void theInboxIsNotFoundForAMissingPostingAndForADraftTheCallerMayNotEdit() {
        assertThatThrownBy(() -> service.inbox(JOB_ID, caller, firstPage)).isInstanceOf(NotFoundException.class);

        serve(job(organization, JobStatus.DRAFT));
        assertThatThrownBy(() -> service.inbox(JOB_ID, caller, firstPage)).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(storage);
        verify(applications, never()).findInbox(any(), any());
    }

    @Test
    void theInboxRefusesAnEditorAndServesARecruiter() {
        serve(job(organization, JobStatus.OPEN));
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        assertThatThrownBy(() -> service.inbox(JOB_ID, caller, firstPage)).isInstanceOf(ForbiddenException.class);

        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));
        when(applications.findInbox(JOB_ID, firstPage)).thenReturn(Page.empty(firstPage));

        assertThat(service.inbox(JOB_ID, caller, firstPage)).isEmpty();
    }

    @Test
    void theInboxPresignsEachRowOnceAndServesTheApplicantAsAUserRef() {
        Job job = job(poster, JobStatus.OPEN);
        serve(job);
        JobApplication first = row(job, caller, "prvi.pdf", Instant.parse("2026-02-02T10:00:00Z"));
        JobApplication second = row(job, poster, "drugi.pdf", Instant.parse("2026-02-01T10:00:00Z"));
        when(applications.findInbox(JOB_ID, firstPage))
                .thenReturn(new PageImpl<>(List.of(first, second), firstPage, 2));
        when(storage.presignCv("prvi.pdf")).thenReturn("https://minio/prvi");
        when(storage.presignCv("drugi.pdf")).thenReturn("https://minio/drugi");

        List<JobApplicationDto> rows = service.inbox(JOB_ID, poster, firstPage).getContent();

        assertThat(rows).extracting(row -> row.cvUrl()).containsExactly("https://minio/prvi", "https://minio/drugi");
        assertThat(rows).extracting(row -> row.applicant().handle()).containsExactly("caller", "poster");
        assertThat(rows).extracting(row -> row.applicant().actorType())
                .containsOnly(ActorType.USER);
        verify(storage, times(1)).presignCv("prvi.pdf");
        verify(storage, times(1)).presignCv("drugi.pdf");
    }

    private void serve(Job job) {
        when(jobs.findWithCreatedFor(JOB_ID)).thenReturn(Optional.of(job));
    }

    private void stubStore(Job job) {
        when(storage.putCv(any(), any())).thenReturn(KEY);
        when(storage.presignCv(KEY)).thenReturn("https://minio/signed");
        when(users.getReferenceById(CALLER_ID)).thenReturn(caller);
        when(applications.saveAndFlush(any())).thenAnswer(call -> {
            JobApplication saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ROW_ID);
            ReflectionTestUtils.setField(saved, "submittedAt", Instant.parse("2026-02-02T10:00:00Z"));
            return saved;
        });
        when(jobs.findWithCreatedFor(JOB_ID)).thenReturn(Optional.of(job));
    }

    private static MultipartFile file(byte[] bytes) {
        return new MockMultipartFile("cv", "zivotopis.pdf", "application/pdf", bytes);
    }

    private static JobApplication row(Job job, User applicant, String key, Instant submittedAt) {
        JobApplication application = new JobApplication(job, applicant, key);
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(application, "submittedAt", submittedAt);
        return application;
    }

    private static Job job(Actor createdFor, JobStatus status) {
        Job job = TestFixtures.job(TestFixtures.user("writer"), createdFor, "oglas-za-posao-1000", status);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }

    private static void assertUnprocessable(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(UnprocessableException.class,
                ex -> assertThat(ex.getErrors()).containsOnlyKeys(field));
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> {
            sync.afterCommit();
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        });
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
