package io.github.matijaradovic.spona.job.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.job.Job;

public record JobListItemDto(
        UUID id,
        String slug,
        String title,
        JobPosterDto createdFor,
        String location,
        SalaryDto salary,
        Instant createdAt,
        List<String> keywords) {

    public static JobListItemDto from(Job job) {
        return new JobListItemDto(job.getId(), job.getSlug(), job.getTitle(), JobPosterDto.from(job.getCreatedFor()),
                job.getLocation(), SalaryDto.from(job.getSalary()), job.getCreatedAt(), job.getKeywords());
    }
}
