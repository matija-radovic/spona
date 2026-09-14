package io.github.matijaradovic.spona.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.post.dto.CreatePostRequest;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.post.dto.UpdatePostRequest;
import io.github.matijaradovic.spona.reaction.ReactionService;
import io.github.matijaradovic.spona.reaction.ReactionTargetType;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class PostService {

    private final PostRepository posts;
    private final ActorRepository actors;
    private final UserRepository users;
    private final CommentRepository comments;
    private final FollowRepository follows;
    private final ContentAuthority authority;
    private final ReactionService reactions;

    public PostService(PostRepository posts, ActorRepository actors, UserRepository users,
            CommentRepository comments, FollowRepository follows, ContentAuthority authority,
            ReactionService reactions) {
        this.posts = posts;
        this.actors = actors;
        this.users = users;
        this.comments = comments;
        this.follows = follows;
        this.authority = authority;
        this.reactions = reactions;
    }

    @Transactional(readOnly = true)
    public PostDto post(UUID postId, User viewer) {
        return dtos(List.of(load(postId)), viewer).getFirst();
    }

    /** Rows must carry a fetched {@code createdFor}. */
    @Transactional(readOnly = true)
    public List<PostDto> dtos(List<Post> rows, User viewer) {
        List<UUID> ids = rows.stream().map(post -> post.getId()).toList();
        Map<UUID, Long> commentCounts = comments.commentCounts(ids);
        Map<UUID, ReactionSummaryDto> summaries = reactions.summaries(ReactionTargetType.POST, ids, viewer);
        Set<UUID> followed = viewer == null
                ? Set.of()
                : follows.followeesAmong(viewer.getId(),
                        rows.stream().map(post -> post.getCreatedFor().getId()).distinct().toList());
        Function<Post, Boolean> canManage = managerCheck(viewer);
        return rows.stream()
                .map(post -> PostDto.from(post, commentCounts.getOrDefault(post.getId(), 0L),
                        summaries.get(post.getId()),
                        viewer == null ? null : followed.contains(post.getCreatedFor().getId()),
                        canManage.apply(post)))
                .toList();
    }

    @Transactional
    public PostDto create(User caller, CreatePostRequest request) {
        String content = checkedContent(request.content());
        Actor author = actors.findById(request.createdForId())
                .orElseThrow(() -> new NotFoundException("Actor not found."));
        if (!authority.canManage(author, caller)) {
            throw new ForbiddenException("You may not publish as this actor.");
        }
        Post post = posts.saveAndFlush(new Post(users.getReferenceById(caller.getId()), author, content));
        return dto(post, 0, ReactionSummaryDto.of(List.of(), List.of()), caller, true);
    }

    @Transactional
    public PostDto update(UUID postId, User caller, UpdatePostRequest request) {
        String content = checkedContent(request.content());
        Post post = load(postId);
        if (!authority.canManage(post.getCreatedFor(), caller)) {
            throw new ForbiddenException("You may not edit this post.");
        }
        post.setContent(content);
        return dto(post, commentCount(postId), reactions.summary(ReactionTargetType.POST, postId, caller), caller,
                true);
    }

    @Transactional
    public void delete(UUID postId, User caller) {
        Post post = load(postId);
        if (!authority.canManage(post.getCreatedFor(), caller)) {
            throw new ForbiddenException("You may not delete this post.");
        }
        posts.delete(post);
    }

    private Post load(UUID postId) {
        return posts.findWithCreatedFor(postId).orElseThrow(() -> new NotFoundException("Post not found."));
    }

    private long commentCount(UUID postId) {
        return comments.commentCounts(List.of(postId)).getOrDefault(postId, 0L);
    }

    private PostDto dto(Post post, long commentCount, ReactionSummaryDto summary, User viewer, Boolean canManage) {
        Boolean followed = viewer == null
                ? null
                : follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), post.getCreatedFor().getId());
        return PostDto.from(post, commentCount, summary, followed, canManage);
    }

    private Function<Post, Boolean> managerCheck(User viewer) {
        if (viewer == null) {
            return post -> null;
        }
        Map<UUID, Boolean> answers = new HashMap<>();
        return post -> answers.computeIfAbsent(
                post.getCreatedFor().getId(),
                id -> authority.canManage(post.getCreatedFor(), viewer));
    }

    private static String checkedContent(String content) {
        String trimmed = Strings.trimToEmpty(content);
        if (trimmed.isEmpty()) {
            throw UnprocessableException.on("content", "Content is required.");
        }
        if (trimmed.length() > Post.CONTENT_MAX) {
            throw UnprocessableException.on("content", "At most " + Post.CONTENT_MAX + " characters.");
        }
        return trimmed;
    }
}
