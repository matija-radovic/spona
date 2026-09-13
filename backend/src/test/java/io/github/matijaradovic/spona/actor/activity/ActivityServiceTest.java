package io.github.matijaradovic.spona.actor.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.activity.dto.ActivityItemDto;
import io.github.matijaradovic.spona.actor.activity.dto.ActivityItemDto.ActivityCommentDto;
import io.github.matijaradovic.spona.actor.activity.dto.ActivityItemDto.ActivityPostDto;
import io.github.matijaradovic.spona.comment.Comment;
import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ActivityServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a3");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a4");
    private static final Pageable FIRST = PageRequest.of(0, 20);

    private final ActorRepository actors = mock(ActorRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final ActivityService service = new ActivityService(actors, posts, comments, new ContentAuthority(roles));

    private final User viewer = TestFixtures.userWithId("viewer", VIEWER_ID);

    @Test
    void anUnknownActorIs404() {
        when(actors.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activity(USER_ID, "all", FIRST, viewer))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInvalidKindIs422() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.activity(USER_ID, "bogus", FIRST, viewer))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void aGuestGetsNeitherAuthorityFlag() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        Post post = post(POST_ID, "hello");
        when(posts.findByCreatedFor(USER_ID, FIRST)).thenReturn(new PageImpl<>(List.of(post), FIRST, 1));
        when(comments.commentCounts(anyCollection())).thenReturn(Map.of());

        ActivityPostDto dto = (ActivityPostDto) service.activity(USER_ID, "posts", FIRST, null)
                .getContent().get(0);

        assertThat(dto.viewerCanEdit()).isNull();
        assertThat(dto.viewerCanDelete()).isNull();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void theProfileOwnerMayManageTheirOwnPosts() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        Post post = post(POST_ID, "hello");
        when(posts.findByCreatedFor(USER_ID, FIRST)).thenReturn(new PageImpl<>(List.of(post), FIRST, 1));
        when(comments.commentCounts(anyCollection())).thenReturn(Map.of());

        Page<ActivityItemDto> page = service.activity(USER_ID, "posts", FIRST, owner);

        ActivityPostDto dto = (ActivityPostDto) page.getContent().get(0);
        assertThat(dto.kind()).isEqualTo("post");
        assertThat(dto.viewerCanEdit()).isTrue();
        assertThat(dto.viewerCanDelete()).isTrue();
    }

    @Test
    void anotherUserMayNotManageSomeonesElsePosts() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        Post post = post(POST_ID, "hello");
        when(posts.findByCreatedFor(USER_ID, FIRST)).thenReturn(new PageImpl<>(List.of(post), FIRST, 1));
        when(comments.commentCounts(anyCollection())).thenReturn(Map.of());

        ActivityPostDto dto = (ActivityPostDto) service.activity(USER_ID, "posts", FIRST, viewer)
                .getContent().get(0);

        assertThat(dto.viewerCanEdit()).isFalse();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void anEditorMayManageOrganizationPostsButARecruiterMayNot() {
        Organization org = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(org, "id", ORG_ID);
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(org));
        Post post = post(POST_ID, "hello");
        when(posts.findByCreatedFor(ORG_ID, FIRST)).thenReturn(new PageImpl<>(List.of(post), FIRST, 1));
        when(comments.commentCounts(anyCollection())).thenReturn(Map.of());
        when(roles.findLiveRole(ORG_ID, VIEWER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        ActivityPostDto asEditor = (ActivityPostDto) service.activity(ORG_ID, "posts", FIRST, viewer)
                .getContent().get(0);
        assertThat(asEditor.viewerCanEdit()).isTrue();

        when(roles.findLiveRole(ORG_ID, VIEWER_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));
        ActivityPostDto asRecruiter = (ActivityPostDto) service.activity(ORG_ID, "posts", FIRST, viewer)
                .getContent().get(0);
        assertThat(asRecruiter.viewerCanEdit()).isFalse();
    }

    @Test
    void aPostsCommentCountIsRootsPlusReplies() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        Post post = post(POST_ID, "hello");
        when(posts.findByCreatedFor(USER_ID, FIRST)).thenReturn(new PageImpl<>(List.of(post), FIRST, 1));
        when(comments.commentCounts(List.of(POST_ID))).thenReturn(Map.of(POST_ID, 4L));

        ActivityPostDto dto = (ActivityPostDto) service.activity(USER_ID, "posts", FIRST, null)
                .getContent().get(0);

        assertThat(dto.commentCount()).isEqualTo(4L);
    }

    @Test
    void aReplysResponseCountIsAlwaysZeroAndNeverQueried() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        UUID rootId = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
        UUID replyId = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
        Comment root = comment(rootId, "root", null);
        Comment reply = comment(replyId, "reply", root);
        when(comments.findByCreatedFor(USER_ID, FIRST))
                .thenReturn(new PageImpl<>(List.of(root, reply), FIRST, 2));
        when(comments.replyCounts(List.of(rootId))).thenReturn(Map.of(rootId, 2L));

        List<ActivityItemDto> items = service.activity(USER_ID, "comments", FIRST, null).getContent();

        ActivityCommentDto rootDto = (ActivityCommentDto) items.get(0);
        ActivityCommentDto replyDto = (ActivityCommentDto) items.get(1);
        assertThat(rootDto.isReply()).isFalse();
        assertThat(rootDto.responseCount()).isEqualTo(2L);
        assertThat(replyDto.isReply()).isTrue();
        assertThat(replyDto.responseCount()).isZero();
        verify(comments, never()).replyCounts(List.of(replyId));
    }

    @Test
    void allMergesPostsAndCommentsInTheRowOrderTheUnionQueryGives() {
        User owner = TestFixtures.userWithId("owner", USER_ID);
        when(actors.findById(USER_ID)).thenReturn(Optional.of(owner));
        UUID commentId = UUID.fromString("00000000-0000-4000-8000-0000000000c9");
        Post post = post(POST_ID, "hello");
        Comment comment = comment(commentId, "hi", null);

        when(posts.countActivityRows(USER_ID)).thenReturn(2L);
        when(posts.findActivityRowIds(USER_ID, FIRST.getPageSize(), FIRST.getOffset())).thenReturn(List.of(
                new Object[] { commentId, "comment" },
                new Object[] { POST_ID, "post" }));
        when(posts.findAllById(List.of(POST_ID))).thenReturn(List.of(post));
        when(comments.findAllById(List.of(commentId))).thenReturn(List.of(comment));
        when(comments.commentCounts(List.of(POST_ID))).thenReturn(Map.of());
        when(comments.replyCounts(List.of(commentId))).thenReturn(Map.of());

        Page<ActivityItemDto> page = service.activity(USER_ID, "all", FIRST, null);

        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getContent().get(0)).isInstanceOf(ActivityCommentDto.class);
        assertThat(page.getContent().get(1)).isInstanceOf(ActivityPostDto.class);
    }

    private static Post post(UUID id, String content) {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(id);
        when(post.getContent()).thenReturn(content);
        when(post.getCreatedAt()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
        return post;
    }

    private static Comment comment(UUID id, String content, Comment parent) {
        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(id);
        when(comment.getContent()).thenReturn(content);
        when(comment.getCreatedAt()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
        when(comment.getResponseToComment()).thenReturn(parent);
        Post post = post(POST_ID, "hello");
        when(comment.getPost()).thenReturn(post);
        return comment;
    }
}
