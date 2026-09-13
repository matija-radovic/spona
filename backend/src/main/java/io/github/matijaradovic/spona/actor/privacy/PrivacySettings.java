package io.github.matijaradovic.spona.actor.privacy;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "privacy_settings")
public class PrivacySettings {

    @Id
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "show_job_postings", nullable = false)
    private boolean showJobPostings = true;

    protected PrivacySettings() {
    }

    public PrivacySettings(UUID actorId) {
        this.actorId = actorId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public boolean isShowJobPostings() {
        return showJobPostings;
    }

    public void setShowJobPostings(boolean showJobPostings) {
        this.showJobPostings = showJobPostings;
    }
}
