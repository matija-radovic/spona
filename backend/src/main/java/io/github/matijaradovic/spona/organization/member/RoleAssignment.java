package io.github.matijaradovic.spona.organization.member;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.matijaradovic.spona.organization.Organization;
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
@Table(name = "role_assignment")
public class RoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, updatable = false)
    private OrganizationRole role;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Whoever granted it, who may delete their account without that taking this row. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", updatable = false)
    private User assignedBy;

    protected RoleAssignment() {
    }

    public RoleAssignment(User user, Organization organization, OrganizationRole role,
            User assignedBy) {
        this.user = user;
        this.organization = organization;
        this.role = role;
        this.assignedBy = assignedBy;
    }

    /** Stamping the row is what frees `role_assignment_live_key` for the next grant. */
    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public User getAssignedBy() {
        return assignedBy;
    }
}
