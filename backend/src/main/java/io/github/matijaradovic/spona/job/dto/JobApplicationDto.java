package io.github.matijaradovic.spona.job.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.job.application.JobApplication;

public record JobApplicationDto(
        UUID id,
        ActorRefDto applicant,
        String cvUrl,
        Instant submittedAt) {

    public static JobApplicationDto from(JobApplication application, String cvUrl) {
        return new JobApplicationDto(
                application.getId(),
                ActorRefDto.from(application.getCreatedBy()),
                cvUrl,
                application.getSubmittedAt());
    }
}
