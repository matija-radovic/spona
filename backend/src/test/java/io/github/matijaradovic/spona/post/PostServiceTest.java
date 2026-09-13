package io.github.matijaradovic.spona.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.post.dto.CreatePostRequest;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.post.dto.UpdatePostRequest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class PostServiceTest {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final UUID STRANGER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d3");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d4");

    private final PostRepository posts = mock(PostRepository.class);
    private final ActorRepository actors = mock(ActorRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final PostService service = new PostService(posts, actors, users, comments, follows,
            new ContentAuthority(roles));

    private final User author = TestFixtures.userWithId("author", AUTHOR_ID);
    private final User stranger = TestFixtures.userWithId("stranger", STRANGER_ID);
    private final Organization organization = organization();

    @BeforeEach
    void stubWrites() {
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(invocation -> stored(invocation.getArgument(0)));
        when(users.getReferenceById(AUTHOR_ID)).thenReturn(author);
    }

    @Test
    void aSelfAuthoredPostIsStoredTrimmedAndServedWithBothFlags() {
        when(actors.findById(AUTHOR_ID)).thenReturn(Optional.of(author));

        PostDto dto = service.create(author, new CreatePostRequest("  Zdravo svima \n", AUTHOR_ID));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(posts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("Zdravo svima");
        assertThat(saved.getValue().getCreatedBy()).isSameAs(author);
        assertThat(saved.getValue().getCreatedFor()).isSameAs(author);
        assertThat(dto.id()).isEqualTo(POST_ID);
        assertThat(dto.commentCount()).isZero();
        assertThat(dto.viewerCanEdit()).isTrue();
        assertThat(dto.viewerCanDelete()).isTrue();
        verify(roles, never()).findLiveRole(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "OWNER", "ADMIN", "PUBLISHER", "EDITOR" })
    void aContentRolePublishesAsTheOrganization(OrganizationRole role) {
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(role));

        PostDto dto = service.create(author, new CreatePostRequest("Objava", ORG_ID));

        assertThat(dto.author().id()).isEqualTo(ORG_ID);
        assertThat(dto.viewerCanEdit()).isTrue();
    }

    @Test
    void aRecruiterMayNotPublishAsTheOrganization() {
        when(actors.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.create(author, new CreatePostRequest("Objava", ORG_ID)))
                .isInstanceOf(ForbiddenException.class);
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    void publishingAsAnotherUserIs403() {
        when(actors.findById(STRANGER_ID)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.create(author, new CreatePostRequest("Objava", STRANGER_ID)))
                .isInstanceOf(ForbiddenException.class);
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    void publishingAsNobodyIs404() {
        when(actors.findById(ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(author, new CreatePostRequest("Objava", ORG_ID)))
                .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   ", "\n\t " })
    void blankContentIs422BeforeAnythingIsRead(String content) {
        assertThatThrownBy(() -> service.create(author, new CreatePostRequest(content, AUTHOR_ID)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("content"));
        verifyNoInteractions(actors, posts);
    }

    @Test
    void theBoundIsMeasuredAfterTrimming() {
        when(actors.findById(AUTHOR_ID)).thenReturn(Optional.of(author));
        String atTheBound = "a".repeat(Post.CONTENT_MAX);

        PostDto dto = service.create(author, new CreatePostRequest("  " + atTheBound + "  ", AUTHOR_ID));

        assertThat(dto.content()).isEqualTo(atTheBound);
    }

    @Test
    void contentOverTheBoundIs422() {
        String over = "a".repeat(Post.CONTENT_MAX + 1);

        assertThatThrownBy(() -> service.create(author, new CreatePostRequest(over, AUTHOR_ID)))
                .isInstanceOfSatisfying(UnprocessableException.class,
                        ex -> assertThat(ex.getErrors()).containsKey("content"));
        verifyNoInteractions(actors, posts);
    }

    @Test
    void aGuestReadsThePostWithNoViewerFields() {
        Post post = stored(new Post(author, author, "Zdravo"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));
        when(comments.commentCounts(List.of(POST_ID))).thenReturn(Map.of(POST_ID, 3L));

        PostDto dto = service.post(POST_ID, null);

        assertThat(dto.commentCount()).isEqualTo(3);
        assertThat(dto.authorFollowedByViewer()).isNull();
        assertThat(dto.viewerCanEdit()).isNull();
        assertThat(dto.viewerCanDelete()).isNull();
        verifyNoInteractions(roles, follows);
    }

    @Test
    void aSignedInReaderGetsTheFollowEdgeAndTheBio() {
        author.setBio("Backend developer");
        Post post = stored(new Post(author, author, "Zdravo"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));
        when(follows.existsByFollower_IdAndFollowee_Id(STRANGER_ID, AUTHOR_ID)).thenReturn(true);

        PostDto dto = service.post(POST_ID, stranger);

        assertThat(dto.authorBio()).isEqualTo("Backend developer");
        assertThat(dto.authorFollowedByViewer()).isTrue();
        assertThat(dto.viewerCanEdit()).isFalse();
        assertThat(dto.viewerCanDelete()).isFalse();
    }

    @Test
    void anOrganizationAuthorServesItsDescriptionAsTheBio() {
        organization.setDescription("Softverska kuca");
        Post post = stored(new Post(author, organization, "Objava"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));

        PostDto dto = service.post(POST_ID, null);

        assertThat(dto.author().handle()).isEqualTo("acme");
        assertThat(dto.authorBio()).isEqualTo("Softverska kuca");
    }

    @Test
    void anUnknownPostIs404() {
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.post(POST_ID, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theAuthorEditsTheirOwnPost() {
        Post post = stored(new Post(author, author, "Staro"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));

        PostDto dto = service.update(POST_ID, author, new UpdatePostRequest("  Novo  "));

        assertThat(post.getContent()).isEqualTo("Novo");
        assertThat(dto.content()).isEqualTo("Novo");
        assertThat(dto.viewerCanEdit()).isTrue();
    }

    @Test
    void anEditorManagesWhatSomeoneElsePublishedAsTheOrganization() {
        Post post = stored(new Post(stranger, organization, "Staro"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        service.update(POST_ID, author, new UpdatePostRequest("Novo"));
        service.delete(POST_ID, author);

        assertThat(post.getContent()).isEqualTo("Novo");
        verify(posts).delete(post);
    }

    @Test
    void authorshipGrantsNothingOnOrganizationContent() {
        Post post = stored(new Post(author, organization, "Staro"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));
        when(roles.findLiveRole(ORG_ID, AUTHOR_ID)).thenReturn(Optional.of(OrganizationRole.RECRUITER));

        assertThatThrownBy(() -> service.update(POST_ID, author, new UpdatePostRequest("Novo")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(POST_ID, author)).isInstanceOf(ForbiddenException.class);
        assertThat(post.getContent()).isEqualTo("Staro");
        verify(posts, never()).delete(any());
    }

    @Test
    void aStrangerMayNeitherEditNorDeleteAUsersPost() {
        Post post = stored(new Post(author, author, "Staro"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.update(POST_ID, stranger, new UpdatePostRequest("Novo")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(POST_ID, stranger)).isInstanceOf(ForbiddenException.class);
        assertThat(post.getContent()).isEqualTo("Staro");
        verify(posts, never()).delete(any());
        verify(roles, never()).findLiveRole(any(), any());
    }

    @Test
    void anEditOverTheBoundIs422BeforeThePostIsRead() {
        String over = "a".repeat(Post.CONTENT_MAX + 1);

        assertThatThrownBy(() -> service.update(POST_ID, author, new UpdatePostRequest(over)))
                .isInstanceOf(UnprocessableException.class);
        verify(posts, never()).findWithCreatedFor(any());
    }

    @Test
    void theAuthorDeletesTheirOwnPost() {
        Post post = stored(new Post(author, author, "Zdravo"));
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.of(post));

        service.delete(POST_ID, author);

        verify(posts).delete(post);
    }

    @Test
    void deletingAnUnknownPostIs404() {
        when(posts.findWithCreatedFor(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(POST_ID, author)).isInstanceOf(NotFoundException.class);
    }

    private static Post stored(Post post) {
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        return post;
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
