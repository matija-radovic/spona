package io.github.matijaradovic.spona.organization.invitation;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
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
@Table(name = "organization_invitations")
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, updatable = false)
    private OrganizationRole role;

    /** Null while an address invitation waits for someone to prove they own the address. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_user_id")
    private User invitee;

    /** Lowercased, a snapshot of the invitee's address on a username invitation */
    @Column(name = "invitee_email", updatable = false)
    private String inviteeEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id", updatable = false)
    private User invitedBy;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InvitationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    protected OrganizationInvitation() {
    }

    public OrganizationInvitation(Organization organization, OrganizationRole role, User invitee,
            String inviteeEmail, User invitedBy) {
        this.organization = organization;
        this.role = role;
        this.invitee = invitee;
        this.inviteeEmail = inviteeEmail;
        this.invitedBy = invitedBy;
        this.status = InvitationStatus.WAITING;
    }

    /** The invitee, or on an unbound row whoever holds the address. */
    public boolean isAddressedTo(User user) {
        return invitee != null
                ? invitee.getId().equals(user.getId())
                : inviteeEmail.equalsIgnoreCase(user.getEmail());
    }

    public void bind(User user) {
        this.invitee = user;
    }

    /** Binds an unbound row in the same UPDATE, so it never enters `invitation_waiting_user_key`. */
    public void accept(User user, Instant at) {
        if (invitee == null) {
            invitee = user;
        }
        resolve(InvitationStatus.ACCEPTED, at);
    }

    public void decline(Instant at) {
        resolve(InvitationStatus.DECLINED, at);
    }

    public void cancel(Instant at) {
        resolve(InvitationStatus.CANCELED, at);
    }

    public void dismiss(Instant at) {
        if (dismissedAt == null) {
            dismissedAt = at;
        }
    }

    private void resolve(InvitationStatus outcome, Instant at) {
        this.status = outcome;
        this.resolvedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public User getInvitee() {
        return invitee;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }
}
