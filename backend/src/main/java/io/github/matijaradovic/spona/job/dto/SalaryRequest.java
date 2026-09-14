package io.github.matijaradovic.spona.job.dto;

import java.math.BigDecimal;

import io.github.matijaradovic.spona.job.SalaryPeriod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record SalaryRequest(
        @NotNull(message = "A minimum is required.")
        @PositiveOrZero(message = "Must not be negative.")
        @DecimalMax(value = SalaryRequest.CEILING, message = "Too large.")
        BigDecimal min,

        @PositiveOrZero(message = "Must not be negative.")
        @DecimalMax(value = SalaryRequest.CEILING, message = "Too large.")
        BigDecimal max,

        @NotNull(message = "A currency is required.")
        @Pattern(regexp = "[A-Z]{3}", message = "Three capital letters.")
        String currency,

        @NotNull(message = "A period is required.")
        SalaryPeriod period) {

    /** The largest {@code numeric(12,2)}. */
    public static final String CEILING = "9999999999.99";
}
