package io.github.matijaradovic.spona.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.job.application.JobApplicationRepository;
import io.github.matijaradovic.spona.job.dto.JobPosterDto;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class JobPostingsServiceTest {

    private static final UUID POSTER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d3");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d4");
    private static final UUID NOBODY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d5");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final JobRepository jobs = mock(JobRepository.class);
    private final ActorRepository actors = mock(ActorRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final PrivacyService privacy = mock(PrivacyService.class);
    private final JobService service = new JobService(jobs, mock(JobSearch.class), actors,
            mock(UserRepository.class), mock(FollowRepository.class), new ContentAuthority(roles), privacy,
            mock(JobApplicationRepository.class));

    private final Pageable firstPage = PageMapper.toPageable(1, 20);
    private final User poster = TestFixtures.userWithId("poster", POSTER_ID);
    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final Organization organization = organization();

    @BeforeEach
    void stubReads() {
        when(actors.findById(POSTER_ID)).thenReturn(Optional.of(poster));
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(privacy.of(any())).thenReturn(new PrivacyDto(true));
        when(jobs.findByCreatedFor(any(), any(), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2)));
    }

    @Test
    void anUnknownActorIsNotFound() {
        assertThatThrownBy(() -> service.postings(NOBODY_ID, null, null, firstPage, caller))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(jobs);
    }

    @Test
    void aGuestReadsOpenPostingsNewestFirstAndABlankParameterIsAbsent() {
        service.postings(ORG_ID, "", " ", firstPage, null);

        Pageable asked = askedFor(ORG_ID, EnumSet.of(JobStatus.OPEN));
        assertThat(asked.getPageNumber()).isZero();
        assertThat(asked.getPageSize()).isEqualTo(20);
        assertThat(asked.getSort()).isEqualTo(NEWEST_FIRST);
        verifyNoInteractions(roles);
    }

    @Test
    void anEditorReadsThePublicShape() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        service.postings(ORG_ID, null, null, firstPage, caller);

        assertThat(askedFor(ORG_ID, EnumSet.of(JobStatus.OPEN)).getSort()).isEqualTo(NEWEST_FIRST);
    }

    @Test
    void aStatusOrASortIs400ToAnyoneWhoDoesNotManageThePostingsBeforePrivacyIsRead() {
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        assertBadRequest(() -> service.postings(ORG_ID, "DRAFT", null, firstPage, null));
        assertBadRequest(() -> service.postings(ORG_ID, null, "created_asc", firstPage, null));
        assertBadRequest(() -> service.postings(POSTER_ID, "OPEN", null, firstPage, caller));
        assertBadRequest(() -> service.postings(ORG_ID, null, "updated_desc", firstPage, caller));
        verifyNoInteractions(privacy, jobs);
    }

    @Test
    void hiddenPostingsAreAnEmptyPageToAGuestAndAStranger() {
        when(privacy.of(POSTER_ID)).thenReturn(new PrivacyDto(false));

        Page<SavedJobItemDto> guest = service.postings(POSTER_ID, null, null, firstPage, null);
        Page<SavedJobItemDto> stranger = service.postings(POSTER_ID, null, null, firstPage, caller);

        assertThat(guest).isEmpty();
        assertThat(guest.getTotalElements()).isZero();
        assertThat(stranger).isEmpty();
        verifyNoInteractions(jobs);
    }

    @Test
    void thePosterReadsEveryStatusNewestFirstEvenWhenHiddenWithNoRoleOrPrivacyRead() {
        when(privacy.of(POSTER_ID)).thenReturn(new PrivacyDto(false));

        service.postings(POSTER_ID, null, null, firstPage, poster);

        assertThat(askedFor(POSTER_ID, EnumSet.allOf(JobStatus.class)).getSort()).isEqualTo(NEWEST_FIRST);
        verifyNoInteractions(roles, privacy);
    }

    @Test
    void everyRoleButAnEditorNarrowsAnOrganizationsPostingsForOneRoleReadEach() {
        for (OrganizationRole role : List.of(OrganizationRole.RECRUITER, OrganizationRole.PUBLISHER,
                OrganizationRole.ADMIN, OrganizationRole.OWNER)) {
            when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(role));
            service.postings(ORG_ID, "DRAFT", null, firstPage, caller);
        }

        verify(jobs, times(4)).findByCreatedFor(eq(ORG_ID), eq(EnumSet.of(JobStatus.DRAFT)), any());
        verify(roles, times(4)).findLiveRole(ORG_ID, CALLER_ID);
        verifyNoInteractions(privacy);
    }

    @Test
    void theFourSortsOrderOnTheirColumnAndEndOnTheId() {
        List.of("created_desc", "created_asc", "updated_desc", "updated_asc")
                .forEach(sort -> service.postings(POSTER_ID, null, sort, firstPage, poster));

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs, times(4)).findByCreatedFor(eq(POSTER_ID), any(), asked.capture());
        assertThat(asked.getAllValues()).extracting(pageable -> pageable.getSort()).containsExactly(
                NEWEST_FIRST,
                Sort.by(Sort.Direction.ASC, "createdAt", "id"),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"),
                Sort.by(Sort.Direction.ASC, "updatedAt", "id"));
    }

    @Test
    void anUnknownStatusOrSortFromThePosterIs422UnderItsNameBeforeAnyJobRead() {
        assertUnprocessable(() -> service.postings(POSTER_ID, "PENDING", null, firstPage, poster), "status");
        assertUnprocessable(() -> service.postings(POSTER_ID, null, "date_desc", firstPage, poster), "sort");
        verifyNoInteractions(jobs);
    }

    @Test
    void aRowServesItsStatusAndAPersonAsActorTypeAlone() {
        Job draft = TestFixtures.job(poster, poster, "licni-nacrt-1000", JobStatus.DRAFT);
        ReflectionTestUtils.setField(draft, "id", JOB_ID);
        doAnswer(invocation -> new PageImpl<>(List.of(draft), invocation.getArgument(2), 1))
                .when(jobs).findByCreatedFor(any(), any(), any());

        Page<SavedJobItemDto> page = service.postings(POSTER_ID, null, null, firstPage, poster);

        assertThat(page.getContent()).extracting(row -> row.id()).containsExactly(JOB_ID);
        assertThat(page.getContent().getFirst().status()).isEqualTo(JobStatus.DRAFT);
        assertThat(page.getContent().getFirst().createdFor()).isEqualTo(new JobPosterDto.PersonPoster(ActorType.USER));
    }

    private Pageable askedFor(UUID actorId, Collection<JobStatus> statuses) {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).findByCreatedFor(eq(actorId), eq(statuses), pageable.capture());
        return pageable.getValue();
    }

    private static void assertBadRequest(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BadRequestException.class);
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
