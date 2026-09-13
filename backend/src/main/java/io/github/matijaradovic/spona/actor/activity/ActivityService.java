package io.github.matijaradovic.spona.actor.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.activity.dto.ActivityItemDto;
import io.github.matijaradovic.spona.comment.Comment;
import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.user.User;

@Service
public class ActivityService {

    private final ActorRepository actors;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ContentAuthority authority;

    public ActivityService(ActorRepository actors, PostRepository posts, CommentRepository comments,
            ContentAuthority authority) {
        this.actors = actors;
        this.posts = posts;
        this.comments = comments;
        this.authority = authority;
    }

    @Transactional(readOnly = true)
    public Page<ActivityItemDto> activity(UUID actorId, String kind, Pageable pageable, User viewer) {
        Actor owner = actors.findById(actorId).orElseThrow(() -> new NotFoundException("Actor not found."));
        ActorRefDto author = ActorRefDto.from(owner);
        Boolean canManage = viewer == null ? null : authority.canManage(owner, viewer);

        return switch (kind) {
            case "all" -> allPage(actorId, pageable, author, canManage);
            case "posts" -> postsPage(actorId, pageable, author, canManage);
            case "comments" -> commentsPage(actorId, pageable, author, canManage);
            default -> throw UnprocessableException.on("kind", "Kind must be all, posts or comments.");
        };
    }

    private Page<ActivityItemDto> postsPage(UUID actorId, Pageable pageable, ActorRefDto author, Boolean canManage) {
        Page<Post> page = posts.findByCreatedFor(actorId, pageable);
        Map<UUID, Long> commentCounts = comments.commentCounts(page.map(post -> post.getId()).getContent());
        return page.map(post -> ActivityItemDto.post(post, author, commentCounts.getOrDefault(post.getId(), 0L),
                canManage));
    }

    private Page<ActivityItemDto> commentsPage(UUID actorId, Pageable pageable, ActorRefDto author,
            Boolean canManage) {
        Page<Comment> page = comments.findByCreatedFor(actorId, pageable);
        List<UUID> rootIds = page.getContent().stream()
                .filter(comment -> comment.getResponseToComment() == null)
                .map(comment -> comment.getId())
                .toList();
        Map<UUID, Long> replyCounts = comments.replyCounts(rootIds);
        return page.map(comment -> ActivityItemDto.comment(comment, author,
                replyCounts.getOrDefault(comment.getId(), 0L), canManage));
    }

    private Page<ActivityItemDto> allPage(UUID actorId, Pageable pageable, ActorRefDto author, Boolean canManage) {
        long total = posts.countActivityRows(actorId);
        List<Object[]> rows = posts.findActivityRowIds(actorId, pageable.getPageSize(), pageable.getOffset());

        List<UUID> postIds = new ArrayList<>();
        List<UUID> commentIds = new ArrayList<>();
        for (Object[] row : rows) {
            (row[1].equals("post") ? postIds : commentIds).add((UUID) row[0]);
        }

        Map<UUID, Post> postById = posts.findAllById(postIds).stream()
                .collect(Collectors.toMap(post -> post.getId(), post -> post));
        Map<UUID, Comment> commentById = comments.findAllById(commentIds).stream()
                .collect(Collectors.toMap(comment -> comment.getId(), comment -> comment));

        Map<UUID, Long> commentCounts = comments.commentCounts(postIds);
        List<UUID> rootIds = commentIds.stream()
                .filter(id -> commentById.get(id).getResponseToComment() == null)
                .toList();
        Map<UUID, Long> replyCounts = comments.replyCounts(rootIds);

        List<ActivityItemDto> items = rows.stream()
                .map(row -> {
                    UUID id = (UUID) row[0];
                    if (row[1].equals("post")) {
                        return ActivityItemDto.post(postById.get(id), author,
                                commentCounts.getOrDefault(id, 0L), canManage);
                    }
                    return ActivityItemDto.comment(commentById.get(id), author,
                            replyCounts.getOrDefault(id, 0L), canManage);
                })
                .toList();

        return new PageImpl<>(items, pageable, total);
    }
}
