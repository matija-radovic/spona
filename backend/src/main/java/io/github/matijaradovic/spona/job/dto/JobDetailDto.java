package io.github.matijaradovic.spona.job.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.ExperienceLevel;
import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.job.JobCategory;
import io.github.matijaradovic.spona.job.JobStatus;
import io.github.matijaradovic.spona.job.JobType;
import io.github.matijaradovic.spona.job.WorkingHours;
import io.github.matijaradovic.spona.job.WorkplaceType;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

public sealed interface JobDetailDto {

    record OrganizationPosting(
            UUID id,
            String slug,
            String title,
            JobPosterDto createdFor,
            String location,
            SalaryDto salary,
            Instant createdAt,
            List<String> keywords,
            JobCategory category,
            JobType jobType,
            WorkplaceType workplaceType,
            WorkingHours workingHours,
            ExperienceLevel experienceLevel,
            JobStatus status,
            ApplicationType applicationType,
            List<ContactEntry> contactInformation,
            String externalUrl,
            String description,
            String createdForBio,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean createdForFollowedByViewer,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanDelete,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Long applicationCount) implements JobDetailDto {
    }

    /** No bio and no follow edge, since either would identify the person. */
    record PersonPosting(
            UUID id,
            String slug,
            String title,
            JobPosterDto createdFor,
            String location,
            SalaryDto salary,
            Instant createdAt,
            List<String> keywords,
            JobCategory category,
            JobType jobType,
            WorkplaceType workplaceType,
            WorkingHours workingHours,
            ExperienceLevel experienceLevel,
            JobStatus status,
            ApplicationType applicationType,
            List<ContactEntry> contactInformation,
            String externalUrl,
            String description,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanEdit,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Boolean viewerCanDelete,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            Long applicationCount) implements JobDetailDto {
    }

    /**
     * Whoever may not edit a CLOSED posting gets its contact entries and external URL as null, and
     * {@code applicationCount} is null for anyone who may not edit the posting at all.
     */
    static JobDetailDto from(Job job, Boolean followedByViewer, Boolean canEdit, Long applicationCount) {
        boolean withheld = job.getStatus() == JobStatus.CLOSED && !Boolean.TRUE.equals(canEdit);
        List<ContactEntry> contactInformation = withheld ? null : job.getContactInformation();
        String externalUrl = withheld ? null : job.getExternalUrl();
        JobPosterDto createdFor = JobPosterDto.from(job.getCreatedFor());
        SalaryDto salary = SalaryDto.from(job.getSalary());
        return switch (job.getCreatedFor()) {
            case Organization organization -> new OrganizationPosting(job.getId(), job.getSlug(), job.getTitle(),
                    createdFor, job.getLocation(), salary, job.getCreatedAt(), job.getKeywords(), job.getCategory(),
                    job.getJobType(), job.getWorkplaceType(), job.getWorkingHours(), job.getExperienceLevel(),
                    job.getStatus(), job.getApplicationType(), contactInformation, externalUrl, job.getDescription(),
                    organization.getDescription(), followedByViewer, canEdit, canEdit, applicationCount);
            case User _ -> new PersonPosting(job.getId(), job.getSlug(), job.getTitle(), createdFor,
                    job.getLocation(), salary, job.getCreatedAt(), job.getKeywords(), job.getCategory(),
                    job.getJobType(), job.getWorkplaceType(), job.getWorkingHours(), job.getExperienceLevel(),
                    job.getStatus(), job.getApplicationType(), contactInformation, externalUrl, job.getDescription(),
                    canEdit, canEdit, applicationCount);
            default -> throw new IllegalArgumentException("Unknown actor subtype " + job.getCreatedFor().getClass());
        };
    }
}
