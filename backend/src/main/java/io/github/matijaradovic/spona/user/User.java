package io.github.matijaradovic.spona.user;

import java.time.Instant;
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

/** A person's account */
@Entity
@Table(name = "app_user")
@DiscriminatorValue("user")
public class User extends Actor {

    @Column(name = "username", nullable = false)
    private String username;
    
    @Column(name = "full_name", nullable = false)
    private String fullName = "";


    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_digest", nullable = false)
    private String passwordDigest;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "bio")
    private String bio;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "location")
    private String location;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "other_contact_info", nullable = false)
    private List<ContactEntry> otherContactInfo = new ArrayList<>();

    protected User() {
    }

    public User(String username, String email, String passwordDigest) {
        this.username = username;
        this.email = email;
        this.passwordDigest = passwordDigest;
    }

    @Override
    public ActorType getActorType() {
        return ActorType.USER;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordDigest() {
        return passwordDigest;
    }

    public void setPasswordDigest(String passwordDigest) {
        this.passwordDigest = passwordDigest;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
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
