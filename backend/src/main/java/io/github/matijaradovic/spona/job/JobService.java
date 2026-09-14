package io.github.matijaradovic.spona.job;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.job.dto.CreateJobRequest;
import io.github.matijaradovic.spona.job.dto.JobDetailDto;
import io.github.matijaradovic.spona.job.dto.JobListItemDto;
import io.github.matijaradovic.spona.job.dto.JobListQuery;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.job.dto.UpdateJobRequest;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class JobService {

    static final int Q_MAX = 200;
    static final int SLUG_BASE_MAX = 120;
    static final String SLUG_FALLBACK = "oglas";

    private static final Pattern UUID_FORM = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private final JobRepository jobs;
    private final JobSearch search;
    private final ActorRepository actors;
    private final UserRepository users;
    private final FollowRepository follows;
    private final ContentAuthority authority;

    public JobService(JobRepository jobs, JobSearch search, ActorRepository actors, UserRepository users,
            FollowRepository follows, ContentAuthority authority) {
        this.jobs = jobs;
        this.search = search;
        this.actors = actors;
        this.users = users;
        this.follows = follows;
        this.authority = authority;
    }

    @Transactional(readOnly = true)
    public Page<JobListItemDto> list(JobListQuery query, Pageable pageable) {
        return search.page(filter(query), pageable).map(job -> JobListItemDto.from(job));
    }

    @Transactional(readOnly = true)
    public JobDetailDto job(String slug, User viewer) {
        Job job = jobs.findBySlugWithCreatedFor(slug).orElseThrow(() -> notFound());
        Actor poster = job.getCreatedFor();
        Boolean canEdit = viewer == null ? null : authority.canManageJobPostings(poster, viewer);
        if (job.getStatus() == JobStatus.DRAFT && !Boolean.TRUE.equals(canEdit)) {
            throw notFound();
        }
        Boolean followed = viewer == null || poster.getActorType() != ActorType.ORG
                ? null
                : follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), poster.getId());
        return JobDetailDto.from(job, followed, canEdit);
    }

    @Transactional(readOnly = true)
    public List<SavedJobItemDto> saved(String ids, String statuses) {
        Set<JobStatus> wanted = listed("status", statuses, JobStatus.class);
        if (wanted.isEmpty()) {
            throw UnprocessableException.on("status", "At least one status is required.");
        }
        wanted.remove(JobStatus.DRAFT);
        List<UUID> parsed = Arrays.stream(ids.split(","))
                .filter(token -> UUID_FORM.matcher(token).matches())
                .map(UUID::fromString)
                .distinct()
                .toList();
        return jobs.findAllInOrder(parsed, wanted).stream().map(job -> SavedJobItemDto.from(job)).toList();
    }

    @Transactional
    public JobDetailDto create(User caller, CreateJobRequest request) {
        if (request.status() == JobStatus.CLOSED) {
            throw UnprocessableException.on("status", "A posting is created as a draft or open.");
        }
        JobForm form = JobForm.of(request);
        Actor poster = actors.findById(request.createdForId())
                .orElseThrow(() -> new NotFoundException("Actor not found."));
        if (!authority.canManageJobPostings(poster, caller)) {
            throw new ForbiddenException("You may not publish job postings as this actor.");
        }
        Job job = new Job(users.getReferenceById(caller.getId()), poster, uniqueSlug(form.title()), form.title(),
                form.description(), form.location(), request.status(), form.applicationType());
        form.applyTo(job);
        if (job.getStatus() == JobStatus.OPEN) {
            JobForm.checkPublishable(job);
        }
        return dto(jobs.saveAndFlush(job), caller);
    }

    @Transactional
    public JobDetailDto update(UUID jobId, User caller, UpdateJobRequest request) {
        JobForm form = JobForm.of(request);
        Job job = editable(jobId, caller, "You may not edit this posting.");
        boolean published = job.getStatus() != JobStatus.DRAFT;
        if (published && form.applicationType() != job.getApplicationType()) {
            throw new ConflictException("The way to apply is fixed once a posting is published.");
        }
        form.applyTo(job);
        if (published) {
            JobForm.checkPublishable(job);
        }
        job.touch();
        return dto(job, caller);
    }

    @Transactional
    public JobDetailDto setStatus(UUID jobId, User caller, JobStatus status) {
        Job job = editable(jobId, caller, "You may not change this posting's status.");
        if (!job.getStatus().canMoveTo(status)) {
            throw new ConflictException("A posting cannot move from " + job.getStatus() + " to " + status + ".");
        }
        if (status == JobStatus.OPEN) {
            JobForm.checkPublishable(job);
        }
        job.setStatus(status);
        return dto(job, caller);
    }

    @Transactional
    public void delete(UUID jobId, User caller) {
        jobs.delete(editable(jobId, caller, "You may not delete this posting."));
    }

    /** A DRAFT the caller may not edit is the same 404 as a missing posting. */
    private Job editable(UUID jobId, User caller, String refusal) {
        Job job = jobs.findWithCreatedFor(jobId).orElseThrow(() -> notFound());
        if (!authority.canManageJobPostings(job.getCreatedFor(), caller)) {
            throw job.getStatus() == JobStatus.DRAFT ? notFound() : new ForbiddenException(refusal);
        }
        return job;
    }

    private JobDetailDto dto(Job job, User caller) {
        Actor poster = job.getCreatedFor();
        Boolean followed = poster.getActorType() == ActorType.ORG
                ? follows.existsByFollower_IdAndFollowee_Id(caller.getId(), poster.getId())
                : null;
        return JobDetailDto.from(job, followed, true);
    }

    private String uniqueSlug(String title) {
        String base = slugBase(title);
        int bound = 10_000;
        while (true) {
            String slug = base + "-" + ThreadLocalRandom.current().nextInt(bound / 10, bound);
            if (!jobs.existsBySlug(slug)) {
                return slug;
            }
            bound = Math.min(bound * 10, 1_000_000_000);
        }
    }

    static String slugBase(String title) {
        String words = Normalizer.normalize(title.replace("đ", "dj").replace("Đ", "Dj"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (words.length() > SLUG_BASE_MAX) {
            words = words.substring(0, SLUG_BASE_MAX).replaceAll("-+$", "");
        }
        return words.isEmpty() ? SLUG_FALLBACK : words;
    }

    static JobFilter filter(JobListQuery query) {
        String q = Strings.trimToNull(query.q());
        if (q != null && q.length() > Q_MAX) {
            throw new BadRequestException("A search is at most " + Q_MAX + " characters.");
        }
        JobCategory category = given(query.role())
                ? JobCategory.fromSlug(query.role())
                        .orElseThrow(() -> UnprocessableException.on("role", "Unknown role."))
                : null;
        JobFilter.Posted posted = given(query.posted())
                ? JobFilter.Posted.fromValue(query.posted())
                        .orElseThrow(() -> UnprocessableException.on("posted", "Posted must be 24h, week or month."))
                : null;
        JobFilter.Sort sort = given(query.sort())
                ? JobFilter.Sort.fromValue(query.sort())
                        .orElseThrow(() -> UnprocessableException.on("sort", "Sort must be date_desc or date_asc."))
                : q != null ? JobFilter.Sort.RELEVANCE : JobFilter.Sort.DATE_DESC;
        if (given(query.salary()) && !query.salary().equals("1")) {
            throw UnprocessableException.on("salary", "Salary must be 1.");
        }
        return new JobFilter(q, category, posted, sort,
                listed("workplace", query.workplace(), WorkplaceType.class),
                listed("employment", query.employment(), JobType.class),
                listed("hours", query.hours(), WorkingHours.class),
                listed("experience", query.experience(), ExperienceLevel.class),
                given(query.salary()));
    }

    private static <E extends Enum<E>> Set<E> listed(String field, String raw, Class<E> type) {
        Set<E> values = EnumSet.noneOf(type);
        if (!given(raw)) {
            return values;
        }
        for (String token : raw.split(",", -1)) {
            values.add(Arrays.stream(type.getEnumConstants())
                    .filter(constant -> constant.name().equals(token))
                    .findFirst()
                    .orElseThrow(() -> UnprocessableException.on(field, "Unknown " + field + " value.")));
        }
        return values;
    }

    private static boolean given(String value) {
        return value != null && !value.isBlank();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Job not found.");
    }
}
