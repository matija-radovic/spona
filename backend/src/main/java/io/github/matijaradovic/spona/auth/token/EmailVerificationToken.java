package io.github.matijaradovic.spona.auth.token;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.matijaradovic.spona.common.util.SecureTokens;
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

/** One emailed confirmation link, live while `consumedAt` is null */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "purpose", nullable = false, updatable = false)
    private EmailVerificationPurpose purpose;

    /** The address being confirmed, which for a CHANGE is not yet `User.email`. */
    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, updatable = false, length = SecureTokens.HASH_LENGTH)
    private String tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(User user, EmailVerificationPurpose purpose, String email,
            String tokenHash, Instant expiresAt) {
        this.user = user;
        this.purpose = purpose;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** `verification_live_key` admits one such row per user and purpose. */
    public boolean isLive(Instant at) {
        return consumedAt == null && expiresAt.isAfter(at);
    }

    /** Spending the row, which is what frees the partial unique index for the next one. */
    public void consume(Instant at) {
        this.consumedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public EmailVerificationPurpose getPurpose() {
        return purpose;
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
}
