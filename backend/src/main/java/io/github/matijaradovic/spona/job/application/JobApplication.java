package io.github.matijaradovic.spona.job.application;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private Job job;

    /** key inside the private bucket */
    @Column(name = "cv_object_key", nullable = false, length = 512, updatable = false)
    private String cvObjectKey;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    protected JobApplication() {
    }

    public JobApplication(Job job, User createdBy, String cvObjectKey) {
        this.job = job;
        this.createdBy = createdBy;
        this.cvObjectKey = cvObjectKey;
    }

    public UUID getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public String getCvObjectKey() {
        return cvObjectKey;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
