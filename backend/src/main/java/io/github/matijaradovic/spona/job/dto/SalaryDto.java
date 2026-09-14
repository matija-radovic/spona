package io.github.matijaradovic.spona.job.dto;

import java.math.BigDecimal;

import io.github.matijaradovic.spona.job.Salary;
import io.github.matijaradovic.spona.job.SalaryPeriod;

public record SalaryDto(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period, String displayText) {

    public static SalaryDto from(Salary salary) {
        return salary == null
                ? null
                : new SalaryDto(salary.getMin(), salary.getMax(), salary.getCurrency(), salary.getPeriod(),
                        salary.getDisplayText());
    }
}
