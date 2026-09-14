package io.github.matijaradovic.spona.job;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.job.dto.JobDetailDto;
import io.github.matijaradovic.spona.job.dto.JobListItemDto;
import io.github.matijaradovic.spona.job.dto.JobListQuery;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.user.User;

@Service
public class JobService {

    static final int Q_MAX = 200;

    private static final Pattern UUID_FORM = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private final JobRepository jobs;
    private final JobSearch search;
    private final FollowRepository follows;
    private final ContentAuthority authority;

    public JobService(JobRepository jobs, JobSearch search, FollowRepository follows, ContentAuthority authority) {
        this.jobs = jobs;
        this.search = search;
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
