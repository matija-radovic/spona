package io.github.matijaradovic.spona.job.dto;

/** The public list's filters as sent, every one optional; the service parses them. */
public record JobListQuery(
        String q,
        String role,
        String posted,
        String sort,
        String workplace,
        String employment,
        String hours,
        String experience,
        String salary) {
}
