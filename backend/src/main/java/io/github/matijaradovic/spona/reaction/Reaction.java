package io.github.matijaradovic.spona.reaction;

import java.util.UUID;

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
@Table(name = "reaction")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "emoji", nullable = false, updatable = false)
    private String emoji;

    @Column(name = "target_type_id", nullable = false, updatable = false)
    private UUID targetTypeId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    protected Reaction() {
    }

    public Reaction(ReactionTargetType targetType, UUID targetId, User createdBy, String emoji) {
        this.targetTypeId = targetType.getId();
        this.targetId = targetId;
        this.createdBy = createdBy;
        this.emoji = emoji;
    }

    public UUID getId() {
        return id;
    }

    public String getEmoji() {
        return emoji;
    }

    public UUID getTargetTypeId() {
        return targetTypeId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
