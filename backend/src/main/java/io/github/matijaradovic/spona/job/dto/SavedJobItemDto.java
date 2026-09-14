package io.github.matijaradovic.spona.job.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.job.JobStatus;

public record SavedJobItemDto(
        UUID id,
        String slug,
        String title,
        JobPosterDto createdFor,
        String location,
        SalaryDto salary,
        Instant createdAt,
        List<String> keywords,
        JobStatus status) {

    public static SavedJobItemDto from(Job job) {
        return new SavedJobItemDto(job.getId(), job.getSlug(), job.getTitle(), JobPosterDto.from(job.getCreatedFor()),
                job.getLocation(), SalaryDto.from(job.getSalary()), job.getCreatedAt(), job.getKeywords(),
                job.getStatus());
    }
}
