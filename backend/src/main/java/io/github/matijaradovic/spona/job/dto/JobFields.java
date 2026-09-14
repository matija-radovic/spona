package io.github.matijaradovic.spona.job.dto;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.ExperienceLevel;
import io.github.matijaradovic.spona.job.JobCategory;
import io.github.matijaradovic.spona.job.JobType;
import io.github.matijaradovic.spona.job.WorkingHours;
import io.github.matijaradovic.spona.job.WorkplaceType;

public interface JobFields {

    String title();

    String description();

    List<String> keywords();

    JobCategory category();

    JobType jobType();

    WorkplaceType workplaceType();

    WorkingHours workingHours();

    ExperienceLevel experienceLevel();

    String location();

    SalaryRequest salary();

    ApplicationType applicationType();

    List<ContactEntry> contactInformation();

    String externalUrl();
}
