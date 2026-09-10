package io.github.matijaradovic.spona.common.dto;

/** One element of a `ContactInformation` JSONB array. */
public record ContactEntry(String type, String description, String value) {
}
