package io.github.matijaradovic.spona.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.comment.dto.CommentContextDto;
import io.github.matijaradovic.spona.comment.dto.CommentDto;
import io.github.matijaradovic.spona.comment.dto.CreateCommentRequest;
import io.github.matijaradovic.spona.comment.dto.UpdateCommentRequest;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class CommentServiceTest {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID STRANGER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e3");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e4");
    private static final UUID OTHER_POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e5");
    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e6");
    private static final UUID REPLY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e7");
    private static final UUID NEW_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e8");
    private static final UUID NIL = new UUID(0, 0);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10);

    private final CommentRepository comments = mock(CommentRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final ActorRepository actors = mock(ActorRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final CommentService service = new CommentService(comments, posts, actors, users,
            new ContentAuthority(roles));

    private final User author = TestFixtures.userWithId("author", AUTHOR_ID);
    private final User stranger = TestFixtures.userWithId("stranger", STRANGER_ID);
    private final Organization organization = organization();
    private final Post post = stored(new Post(author, author, "Objava"), POST_ID);

    @BeforeEach
    void stubRows() {
        when(posts.existsById(POST_ID)).thenReturn(true);
        when(posts.getReferenceById(POST_ID)).thenReturn(post);
        when(users.getReferenceById(AUTHOR_ID)).thenReturn(author);
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> stored(invocation.getArgument(0), NEW_ID));
    }

    @Test
    void aSelfAuthoredCommentIsStoredTrimmedAndServedWithBothFlags() {
        when(actors.findById(AUTHOR_ID)).thenReturn(Optional.of(author));

        CommentDto dto = service.create(POST_ID, author, new CreateCommentRequest("  Sjajno \n", AUTHOR_ID, null));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("Sjajno");
        assertThat(saved.getValue().getPost()).isSameAs(post);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(author);
        assertThat(saved.getValue().getResponseToComment()).isNull();
        assertThat(dto.id()).isEqualTo(NEW_ID);
        assertThat(dto.postId()).isEqualTo(POST_ID);
        assertThat(dto.parentId()).isNull();
        assertThat(dto.responseCount()).isZero();
        assertThat(dto.viewerCanEdit()).isTrue();
        assertThat(dto.viewerCanDelete()).isTrue();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "OWNER", "ADMIN", "PUBLISHER", "EDITOR" })
    void aContentRoleCommentsAsTheOrganization(OrganizationRole role) {
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(role));

        CommentDto dto = service.create(POST_ID, author, new CreateCommentRequest("Komentar", ORG_ID, null));

        assertThat(dto.author().id()).isEqualTo(ORG_ID);
        assertThat(dto.viewerCanEdit()).isTrue();
    }

    @Test
    void aRecruiterMayNotCommentAsTheOrganization() {
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.create(POST_ID, author, new CreateCommentRequest("Komentar", ORG_ID, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void commentingAsAnotherUserIs403() {
        when(actors.findById(STRANGER_ID)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.create(POST_ID, author,
                new CreateCommentRequest("Komentar", STRANGER_ID, null))).isInstanceOf(ForbiddenException.class);
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void commentingAsNobodyIs404() {
        when(actors.findById(ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(POST_ID, author, new CreateCommentRequest("Komentar", ORG_ID, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void commentingOnAnUnknownPostIs404() {
        assertThatThrownBy(() -> service.create(OTHER_POST_ID, author,
                new CreateCommentRequest("Komentar", AUTHOR_ID, null))).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(actors);
        verify(comments, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   ", "\n\t " })
    void blankContentIs422BeforeAnythingIsRead(String content) {
        assertThatThrownBy(() -> service.create(POST_ID, author, new CreateCommentRequest(content, AUTHOR_ID, null)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("content"));
        verifyNoInteractions(posts, comments, actors);
    }

    @Test
    void theBoundIsMeasuredAfterTrimming() {
        when(actors.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        String atTheBound = "a".repeat(Comment.CONTENT_MAX);

        CommentDto dto = service.create(POST_ID, author,
                new CreateCommentRequest("  " + atTheBound + "  ", AUTHOR_ID, null));

        assertThat(dto.content()).isEqualTo(atTheBound);
        assertThatThrownBy(() -> service.create(POST_ID, author,
                new CreateCommentRequest(atTheBound + "a", AUTHOR_ID, null)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("content"));
    }

    @Test
    void aReplyIsStoredUnderItsRoot() {
        Comment root = stored(new Comment(post, null, stranger, stranger, "Koren"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(root));
        when(actors.findById(AUTHOR_ID)).thenReturn(Optional.of(author));

        CommentDto dto = service.create(POST_ID, author, new CreateCommentRequest("Odgovor", AUTHOR_ID, ROOT_ID));

        assertThat(dto.parentId()).isEqualTo(ROOT_ID);
        assertThat(dto.responseCount()).isZero();
    }

    @Test
    void replyingToAReplyIs422UnderParentId() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        Comment reply = stored(new Comment(post, root, author, author, "Odgovor"), REPLY_ID);
        when(comments.findWithCreatedFor(REPLY_ID)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> service.create(POST_ID, author, new CreateCommentRequest("Dublje", AUTHOR_ID, REPLY_ID)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("parentId"));
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void aParentOnAnotherPostOrNoParentAtAllIs422UnderParentId() {
        Post other = stored(new Post(author, author, "Druga"), OTHER_POST_ID);
        when(comments.findWithCreatedFor(ROOT_ID))
                .thenReturn(Optional.of(stored(new Comment(other, null, author, author, "Tudji"), ROOT_ID)));
        when(comments.findWithCreatedFor(REPLY_ID)).thenReturn(Optional.empty());

        for (UUID parentId : List.of(ROOT_ID, REPLY_ID)) {
            assertThatThrownBy(() -> service.create(POST_ID, author,
                    new CreateCommentRequest("Odgovor", AUTHOR_ID, parentId)))
                    .isInstanceOfSatisfying(UnprocessableException.class,
                            ex -> assertThat(ex.getErrors()).containsKey("parentId"));
        }
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void aGuestReadsTheTopThreadWithNothingWithheldAndNoViewerFields() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        when(comments.findTopRoots(POST_ID, NIL, FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(root)));
        when(comments.replyCounts(List.of(ROOT_ID))).thenReturn(Map.of(ROOT_ID, 4L));

        Page<CommentDto> page = service.thread(POST_ID, "top", null, FIRST_PAGE, null);

        CommentDto dto = page.getContent().get(0);
        assertThat(dto.responseCount()).isEqualTo(4);
        assertThat(dto.viewerCanEdit()).isNull();
        assertThat(dto.viewerCanDelete()).isNull();
        verify(comments, never()).findParentId(any());
        verifyNoInteractions(roles);
    }

    @Test
    void excludingAReplyWithholdsItsRoot() {
        when(comments.findParentId(REPLY_ID)).thenReturn(Optional.of(ROOT_ID));
        when(comments.findRoots(eq(POST_ID), eq(ROOT_ID), any())).thenReturn(Page.empty());

        service.thread(POST_ID, "new", REPLY_ID, FIRST_PAGE, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(comments).findRoots(eq(POST_ID), eq(ROOT_ID), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void excludingARootOrAnUnknownIdWithholdsThatId() {
        when(comments.findParentId(ROOT_ID)).thenReturn(Optional.empty());
        when(comments.findRoots(eq(POST_ID), eq(ROOT_ID), any())).thenReturn(Page.empty());

        service.thread(POST_ID, "old", ROOT_ID, FIRST_PAGE, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(comments).findRoots(eq(POST_ID), eq(ROOT_ID), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt", "id"));
    }

    @Test
    void anUnknownSortIs422UnderSort() {
        assertThatThrownBy(() -> service.thread(POST_ID, "hot", null, FIRST_PAGE, null))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("sort"));
        verifyNoInteractions(comments);
    }

    @Test
    void theThreadOfAnUnknownPostIs404() {
        assertThatThrownBy(() -> service.thread(OTHER_POST_ID, "top", null, FIRST_PAGE, null))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(comments);
    }

    @Test
    void organizationAuthorityIsReadOncePerAuthorOnAPage() {
        Comment first = stored(new Comment(post, null, stranger, organization, "Prvi"), ROOT_ID);
        Comment second = stored(new Comment(post, null, author, organization, "Drugi"), REPLY_ID);
        when(comments.findTopRoots(POST_ID, NIL, FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(first, second)));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        Page<CommentDto> page = service.thread(POST_ID, "top", null, FIRST_PAGE, author);

        assertThat(page.getContent()).allSatisfy(dto -> assertThat(dto.viewerCanEdit()).isTrue());
        verify(roles, times(1)).findLiveRole(ORG_ID, AUTHOR_ID);
    }

    @Test
    void thePostScopedFormIs404ForACommentOnAnotherPost() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(root));

        assertThat(service.comment(ROOT_ID, POST_ID, null).id()).isEqualTo(ROOT_ID);
        assertThatThrownBy(() -> service.comment(ROOT_ID, OTHER_POST_ID, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.responses(ROOT_ID, OTHER_POST_ID, FIRST_PAGE, null))
                .isInstanceOf(NotFoundException.class);
        verify(comments, never()).findResponses(any(), any());
    }

    @Test
    void theContextOfAReplyServesItsParentAndItsPosition() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        Comment reply = stored(new Comment(post, root, stranger, stranger, "Odgovor"), REPLY_ID);
        when(comments.findWithCreatedFor(REPLY_ID)).thenReturn(Optional.of(reply));
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(root));
        when(comments.replyCounts(List.of(ROOT_ID))).thenReturn(Map.of(ROOT_ID, 5L));
        when(comments.countResponsesBefore(REPLY_ID)).thenReturn(2L);

        CommentContextDto context = service.context(REPLY_ID, null, stranger);

        assertThat(context.comment().parentId()).isEqualTo(ROOT_ID);
        assertThat(context.comment().viewerCanEdit()).isTrue();
        assertThat(context.parent().id()).isEqualTo(ROOT_ID);
        assertThat(context.parent().responseCount()).isEqualTo(5);
        assertThat(context.parent().viewerCanEdit()).isFalse();
        assertThat(context.siblingIndex()).isEqualTo(2);
        assertThat(context.siblingTotal()).isEqualTo(5);
    }

    @Test
    void theContextOfARootRanksItAmongEveryRoot() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(root));
        when(comments.countRootsRankedBefore(POST_ID, ROOT_ID)).thenReturn(3L);
        when(comments.countRoots(POST_ID, NIL)).thenReturn(7L);

        CommentContextDto context = service.context(ROOT_ID, null, null);

        assertThat(context.parent()).isNull();
        assertThat(context.siblingIndex()).isEqualTo(3);
        assertThat(context.siblingTotal()).isEqualTo(7);
    }

    @Test
    void anEditorManagesWhatSomeoneElsePublishedAsTheOrganization() {
        Comment root = stored(new Comment(post, null, stranger, organization, "Staro"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(root));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));
        when(comments.replyCounts(List.of(ROOT_ID))).thenReturn(Map.of(ROOT_ID, 4L));

        CommentDto dto = service.update(ROOT_ID, author, new UpdateCommentRequest("  Novo "));

        assertThat(root.getContent()).isEqualTo("Novo");
        assertThat(dto.responseCount()).isEqualTo(4);
        assertThat(service.delete(ROOT_ID, author).deletedCount()).isEqualTo(5);
        verify(comments).delete(root);
    }

    @Test
    void authorshipGrantsNothingOnOrganizationComments() {
        Comment comment = stored(new Comment(post, null, author, organization, "Staro"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(comment));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.update(ROOT_ID, author, new UpdateCommentRequest("Novo")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(ROOT_ID, author)).isInstanceOf(ForbiddenException.class);
        assertThat(comment.getContent()).isEqualTo("Staro");
        verify(comments, never()).delete(any());
    }

    @Test
    void aStrangerMayNeitherEditNorDeleteAUsersComment() {
        Comment comment = stored(new Comment(post, null, author, author, "Staro"), ROOT_ID);
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.update(ROOT_ID, stranger, new UpdateCommentRequest("Novo")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(ROOT_ID, stranger)).isInstanceOf(ForbiddenException.class);
        assertThat(comment.getContent()).isEqualTo("Staro");
        verify(comments, never()).delete(any());
        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void deletingAReplyReportsOneRow() {
        Comment root = stored(new Comment(post, null, author, author, "Koren"), ROOT_ID);
        Comment reply = stored(new Comment(post, root, author, author, "Odgovor"), REPLY_ID);
        when(comments.findWithCreatedFor(REPLY_ID)).thenReturn(Optional.of(reply));

        assertThat(service.delete(REPLY_ID, author).deletedCount()).isEqualTo(1);
        verify(comments).delete(reply);
        verify(comments, never()).replyCounts(any());
    }

    @Test
    void anEditOverTheBoundIs422BeforeTheCommentIsRead() {
        String over = "a".repeat(Comment.CONTENT_MAX + 1);

        assertThatThrownBy(() -> service.update(ROOT_ID, author, new UpdateCommentRequest(over)))
                .isInstanceOf(UnprocessableException.class);
        verify(comments, never()).findWithCreatedFor(any());
    }

    @Test
    void anUnknownCommentIs404() {
        when(comments.findWithCreatedFor(ROOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.comment(ROOT_ID, null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.context(ROOT_ID, null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(ROOT_ID, author, new UpdateCommentRequest("Novo")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(ROOT_ID, author)).isInstanceOf(NotFoundException.class);
    }

    private static <T> T stored(T row, UUID id) {
        ReflectionTestUtils.setField(row, "id", id);
        ReflectionTestUtils.setField(row, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        return row;
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
