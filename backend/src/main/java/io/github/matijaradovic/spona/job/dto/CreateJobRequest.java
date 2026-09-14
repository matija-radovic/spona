package io.github.matijaradovic.spona.job.dto;

import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.ExperienceLevel;
import io.github.matijaradovic.spona.job.JobCategory;
import io.github.matijaradovic.spona.job.JobStatus;
import io.github.matijaradovic.spona.job.JobType;
import io.github.matijaradovic.spona.job.WorkingHours;
import io.github.matijaradovic.spona.job.WorkplaceType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(

        @NotNull(message = "An actor to publish as is required.")
        UUID createdForId,

        String title,
        String description,
        List<String> keywords,
        JobCategory category,
        JobType jobType,
        WorkplaceType workplaceType,
        WorkingHours workingHours,
        ExperienceLevel experienceLevel,
        String location,

        @Valid
        SalaryRequest salary,

        @NotNull(message = "A status is required.")
        JobStatus status,

        @NotNull(message = "An application type is required.")
        ApplicationType applicationType,

        @Valid
        @Size(max = 15, message = "At most 15 entries.")
        List<ContactEntry> contactInformation,

        String externalUrl) implements JobFields {
}
