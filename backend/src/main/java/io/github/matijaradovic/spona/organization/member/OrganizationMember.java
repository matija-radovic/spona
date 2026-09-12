package io.github.matijaradovic.spona.organization.member;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_member")
@IdClass(OrganizationMemberId.class)
public class OrganizationMember {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected OrganizationMember() {
    }

    public OrganizationMember(User user, Organization organization) {
        this.user = user;
        this.organization = organization;
    }

    public User getUser() {
        return user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
