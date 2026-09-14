package io.github.matijaradovic.spona.job;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "salary")
public class Salary {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column(name = "min", nullable = false)
    private BigDecimal min;

    @Column(name = "max")
    private BigDecimal max;

    @Column(name = "currency", nullable = false)
    private String currency;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "period", nullable = false)
    private SalaryPeriod period;

    @Column(name = "display_text")
    private String displayText;

    protected Salary() {
    }

    public Salary(Job job, BigDecimal min, BigDecimal max, String currency, SalaryPeriod period,
            String displayText) {
        this.job = job;
        this.min = min;
        this.max = max;
        this.currency = currency;
        this.period = period;
        this.displayText = displayText;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Job getJob() {
        return job;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public String getCurrency() {
        return currency;
    }

    public SalaryPeriod getPeriod() {
        return period;
    }

    public String getDisplayText() {
        return displayText;
    }
}
