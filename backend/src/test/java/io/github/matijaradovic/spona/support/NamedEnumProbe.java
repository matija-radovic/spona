package io.github.matijaradovic.spona.support;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** smoke test for native Postgres enums - will sb throw a cast error at runtime */
@Entity
@Table(name = "email_verification_tokens")
public class NamedEnumProbe {

    /** SIGNUP confirms the address a user registered with, CHANGE a new one. */
    public enum ProbePurpose {
        SIGNUP,
        CHANGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "purpose", nullable = false)
    private ProbePurpose purpose;

    @Column(name = "email", nullable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected NamedEnumProbe() {
    }

    public NamedEnumProbe(UUID userId, ProbePurpose purpose, String email, String tokenHash) {
        this.userId = userId;
        this.purpose = purpose;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = createdAt.plusSeconds(3600);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProbePurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(ProbePurpose purpose) {
        this.purpose = purpose;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
