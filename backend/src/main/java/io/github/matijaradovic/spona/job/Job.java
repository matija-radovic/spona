package io.github.matijaradovic.spona.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "job")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "keywords", nullable = false)
    private List<String> keywords = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category")
    private JobCategory category;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "job_type")
    private JobType jobType;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "workplace_type")
    private WorkplaceType workplaceType;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "working_hours")
    private WorkingHours workingHours;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "experience_level")
    private ExperienceLevel experienceLevel;

    @Column(name = "location", nullable = false)
    private String location;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "application_type", nullable = false)
    private ApplicationType applicationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_information")
    private List<ContactEntry> contactInformation;

    @Column(name = "external_url")
    private String externalUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_for_id", nullable = false, updatable = false)
    private Actor createdFor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "job", fetch = FetchType.LAZY)
    private Salary salary;

    protected Job() {
    }

    public Job(User createdBy, Actor createdFor, String slug, String title, String description, String location,
            JobStatus status, ApplicationType applicationType) {
        this.createdBy = createdBy;
        this.createdFor = createdFor;
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.location = location;
        this.status = status;
        this.applicationType = applicationType;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public JobCategory getCategory() {
        return category;
    }

    public void setCategory(JobCategory category) {
        this.category = category;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public WorkplaceType getWorkplaceType() {
        return workplaceType;
    }

    public void setWorkplaceType(WorkplaceType workplaceType) {
        this.workplaceType = workplaceType;
    }

    public WorkingHours getWorkingHours() {
        return workingHours;
    }

    public void setWorkingHours(WorkingHours workingHours) {
        this.workingHours = workingHours;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public List<ContactEntry> getContactInformation() {
        return contactInformation;
    }

    public void setContactInformation(List<ContactEntry> contactInformation) {
        this.contactInformation = contactInformation;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Actor getCreatedFor() {
        return createdFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Salary getSalary() {
        return salary;
    }
}
