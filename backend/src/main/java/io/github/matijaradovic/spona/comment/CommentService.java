package io.github.matijaradovic.spona.comment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.comment.dto.CommentContextDto;
import io.github.matijaradovic.spona.comment.dto.CommentDto;
import io.github.matijaradovic.spona.comment.dto.CreateCommentRequest;
import io.github.matijaradovic.spona.comment.dto.DeleteCommentResultDto;
import io.github.matijaradovic.spona.comment.dto.UpdateCommentRequest;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class CommentService {

    /** The 0 UUID, which no row carries, so withholding it withholds nothing. */
    private static final UUID NOTHING = new UUID(0, 0);

    private final CommentRepository comments;
    private final PostRepository posts;
    private final ActorRepository actors;
    private final UserRepository users;
    private final ContentAuthority authority;

    public CommentService(CommentRepository comments, PostRepository posts, ActorRepository actors,
            UserRepository users, ContentAuthority authority) {
        this.comments = comments;
        this.posts = posts;
        this.actors = actors;
        this.users = users;
        this.authority = authority;
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> thread(UUID postId, String sort, UUID exclude, Pageable pageable, User viewer) {
        requirePost(postId);
        Page<Comment> page = switch (sort) {
            case "top" -> comments.findTopRoots(postId, withheld(exclude), pageable);
            case "new" -> comments.findRoots(postId, withheld(exclude), byCreatedAt(pageable, Sort.Direction.DESC));
            case "old" -> comments.findRoots(postId, withheld(exclude), byCreatedAt(pageable, Sort.Direction.ASC));
            default -> throw UnprocessableException.on("sort", "Sort must be top, new or old.");
        };
        Map<UUID, Long> replyCounts = comments.replyCounts(page.map(root -> root.getId()).getContent());
        Function<Comment, Boolean> canManage = managerCheck(viewer);
        return page.map(root -> CommentDto.from(
                root,
                replyCounts.getOrDefault(root.getId(), 0L),
                canManage.apply(root)));
    }

    @Transactional(readOnly = true)
    public CommentDto comment(UUID commentId, UUID postId, User viewer) {
        Comment comment = load(commentId, postId);
        return CommentDto.from(
                comment,
                responseCount(comment),
                managerCheck(viewer).apply(comment));
    }

    @Transactional(readOnly = true)
    public CommentContextDto context(UUID commentId, UUID postId, User viewer) {
        Comment comment = load(commentId, postId);
        Function<Comment, Boolean> canManage = managerCheck(viewer);
        if (comment.getResponseToComment() == null) {
            UUID ownPostId = comment.getPost().getId();
            return new CommentContextDto(
                    CommentDto.from(comment, responseCount(comment), canManage.apply(comment)),
                    null,
                    comments.countRootsRankedBefore(ownPostId, commentId),
                    comments.countRoots(ownPostId, NOTHING));
        }
        Comment parent = load(comment.getResponseToComment().getId(), null);
        long siblingTotal = responseCount(parent);
        return new CommentContextDto(
                CommentDto.from(comment, 0, canManage.apply(comment)),
                CommentDto.from(parent, siblingTotal, canManage.apply(parent)),
                comments.countResponsesBefore(commentId),
                siblingTotal);
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> responses(UUID commentId, UUID postId, Pageable pageable, User viewer) {
        load(commentId, postId);
        Function<Comment, Boolean> canManage = managerCheck(viewer);
        return comments.findResponses(commentId, pageable)
                .map(reply -> CommentDto.from(reply, 0, canManage.apply(reply)));
    }

    @Transactional
    public CommentDto create(UUID postId, User caller, CreateCommentRequest request) {
        String content = checkedContent(request.content());
        requirePost(postId);
        Comment parent = request.parentId() == null ? null : checkedParent(request.parentId(), postId);
        Actor author = actors.findById(request.createdForId())
                .orElseThrow(() -> new NotFoundException("Actor not found."));
        if (!authority.canManage(author, caller)) {
            throw new ForbiddenException("You may not publish as this actor.");
        }
        Comment comment = comments.saveAndFlush(new Comment(posts.getReferenceById(postId), parent,
                users.getReferenceById(caller.getId()), author, content));
        return CommentDto.from(comment, 0, true);
    }

    @Transactional
    public CommentDto update(UUID commentId, User caller, UpdateCommentRequest request) {
        String content = checkedContent(request.content());
        Comment comment = load(commentId, null);
        if (!authority.canManage(comment.getCreatedFor(), caller)) {
            throw new ForbiddenException("You may not edit this comment.");
        }
        comment.setContent(content);
        return CommentDto.from(comment, responseCount(comment), true);
    }

    @Transactional
    public DeleteCommentResultDto delete(UUID commentId, User caller) {
        Comment comment = load(commentId, null);
        if (!authority.canManage(comment.getCreatedFor(), caller)) {
            throw new ForbiddenException("You may not delete this comment.");
        }
        long deletedCount = 1 + responseCount(comment);
        comments.delete(comment);
        return new DeleteCommentResultDto(deletedCount);
    }

    private void requirePost(UUID postId) {
        if (!posts.existsById(postId)) {
            throw new NotFoundException("Post not found.");
        }
    }

    private Comment load(UUID commentId, UUID postId) {
        return comments.findWithCreatedFor(commentId)
                .filter(comment -> postId == null || comment.getPost().getId().equals(postId))
                .orElseThrow(() -> new NotFoundException("Comment not found."));
    }

    private Comment checkedParent(UUID parentId, UUID postId) {
        Comment parent = comments.findWithCreatedFor(parentId)
                .filter(comment -> comment.getPost().getId().equals(postId))
                .orElseThrow(() -> UnprocessableException.on("parentId", "No such comment on this post."));
        if (parent.getResponseToComment() != null) {
            throw UnprocessableException.on("parentId", "A reply cannot be replied to.");
        }
        return parent;
    }

    private UUID withheld(UUID exclude) {
        return exclude == null ? NOTHING : comments.findParentId(exclude).orElse(exclude);
    }

    private long responseCount(Comment comment) {
        if (comment.getResponseToComment() != null) {
            return 0;
        }
        return comments.replyCounts(List.of(comment.getId())).getOrDefault(comment.getId(), 0L);
    }

    private Function<Comment, Boolean> managerCheck(User viewer) {
        if (viewer == null) {
            return comment -> null;
        }
        Map<UUID, Boolean> answers = new HashMap<>();
        return comment -> answers.computeIfAbsent(
                comment.getCreatedFor().getId(),
                id -> authority.canManage(comment.getCreatedFor(), viewer));
    }

    private static Pageable byCreatedAt(Pageable pageable, Sort.Direction direction) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, "createdAt", "id"));
    }

    private static String checkedContent(String content) {
        String trimmed = Strings.trimToEmpty(content);
        if (trimmed.isEmpty()) {
            throw UnprocessableException.on("content", "Content is required.");
        }
        if (trimmed.length() > Comment.CONTENT_MAX) {
            throw UnprocessableException.on("content", "At most " + Comment.CONTENT_MAX + " characters.");
        }
        return trimmed;
    }
}
