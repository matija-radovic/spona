package io.github.matijaradovic.spona.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One element of a `ContactInformation` JSONB array, bounded as `contact_information_ok` is */
public record ContactEntry(

        @NotBlank(message = "A type is required.")
        @Size(max = 32, message = "At most 32 characters.")
        String type,

        @Size(max = 32, message = "At most 32 characters.")
        String description,

        @NotBlank(message = "A value is required.")
        @Size(max = 128, message = "At most 128 characters.")
        String value) {
}
