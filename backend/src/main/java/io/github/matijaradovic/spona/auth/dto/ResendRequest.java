package io.github.matijaradovic.spona.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A malformed address is the only visible failure, everything else is `204` */
public record ResendRequest(

        @NotBlank(message = "An email address is required.")
        @Email(message = "That is not a valid email address.")
        @Size(max = 254, message = "At most 254 characters.")
        String email) {
}
