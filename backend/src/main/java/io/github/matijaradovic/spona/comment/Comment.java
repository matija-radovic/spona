package io.github.matijaradovic.spona.comment;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.post.Post;
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
@Table(name = "comment")
public class Comment {

    public static final int CONTENT_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content", nullable = false)
    private String content;

    /** depth is capped at 1 by the application, not by schema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_to_comment_id", updatable = false)
    private Comment responseToComment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_of_post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_for_id", nullable = false, updatable = false)
    private Actor createdFor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(Post post, Comment responseToComment, User createdBy, Actor createdFor, String content) {
        this.post = post;
        this.responseToComment = responseToComment;
        this.createdBy = createdBy;
        this.createdFor = createdFor;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Comment getResponseToComment() {
        return responseToComment;
    }

    public Post getPost() {
        return post;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Actor getCreatedFor() {
        return createdFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
