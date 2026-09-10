package io.github.matijaradovic.spona.organization;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ContactEntry;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An organization's profile */
@Entity
@Table(name = "organization")
@DiscriminatorValue("org")
public class Organization extends Actor {

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "description")
    private String description;

    @Column(name = "website")
    private String website;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "location")
    private String location;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "other_contact_info", nullable = false)
    private List<ContactEntry> otherContactInfo = new ArrayList<>();

    protected Organization() {
    }

    public Organization(String slug, String displayName, String legalName) {
        this.slug = slug;
        this.displayName = displayName;
        this.legalName = legalName;
    }

    @Override
    public ActorType getActorType() {
        return ActorType.ORG;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<ContactEntry> getOtherContactInfo() {
        return otherContactInfo;
    }

    public void setOtherContactInfo(List<ContactEntry> otherContactInfo) {
        this.otherContactInfo = otherContactInfo == null ? new ArrayList<>() : otherContactInfo;
    }
}
