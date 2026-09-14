package io.github.matijaradovic.spona.job.dto;

import io.github.matijaradovic.spona.job.JobStatus;

import jakarta.validation.constraints.NotNull;

public record JobStatusRequest(
        @NotNull(message = "A status is required.")
        JobStatus status) {
}
