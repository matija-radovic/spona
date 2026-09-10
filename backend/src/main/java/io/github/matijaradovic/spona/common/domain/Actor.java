package io.github.matijaradovic.spona.common.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

@Entity
@Table(name = "actor")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type", discriminatorType = DiscriminatorType.STRING, length = 16)
public abstract class Actor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Actor() {
    }

    /** discriminant */
    public abstract ActorType getActorType();

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Actor actor)) {
            return false;
        }
        return id != null && id.equals(actor.getId());
    }

    @Override
    public final int hashCode() {
        return Actor.class.hashCode();
    }
}
