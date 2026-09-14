package io.github.matijaradovic.spona.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.post.PostService;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.reaction.ReactionService;
import io.github.matijaradovic.spona.reaction.ReactionTargetType;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class FeedServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID FRIEND_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e3");

    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
    private final ReactionService reactions = mock(ReactionService.class);
    private final FeedService service = new FeedService(posts, new PostService(posts, mock(ActorRepository.class),
            mock(UserRepository.class), comments, follows, new ContentAuthority(roles), reactions));

    private final Pageable firstPage = PageMapper.toPageable(1, 10);
    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final User friend = TestFixtures.userWithId("friend", FRIEND_ID);
    private final Organization organization = organization();

    @Test
    void aPageOfMixedAuthorsReadsCountsSummariesAndFollowEdgesOnceEach() {
        Post byOrganization = post(1, organization);
        Post byFriend = post(2, friend);
        Post own = post(3, caller);
        List<UUID> ids = List.of(byOrganization.getId(), byFriend.getId(), own.getId());
        ReactionSummaryDto friendReactions = ReactionSummaryDto.of(List.of(), List.of());
        serve(byOrganization, byFriend, own);
        when(comments.commentCounts(ids)).thenReturn(Map.of(byFriend.getId(), 4L));
        when(reactions.summaries(ReactionTargetType.POST, ids, caller))
                .thenReturn(Map.of(byFriend.getId(), friendReactions));
        when(follows.followeesAmong(CALLER_ID, List.of(ORG_ID, FRIEND_ID, CALLER_ID)))
                .thenReturn(Set.of(ORG_ID, FRIEND_ID));

        Page<FeedBlockDto> page = service.feed(caller, firstPage);

        assertThat(page.getContent()).extracting(block -> ((FeedBlockDto.PostBlock) block).kind())
                .containsOnly("post");
        List<PostDto> rows = rows(page);
        assertThat(rows).extracting(row -> row.id()).containsExactlyElementsOf(ids);
        assertThat(rows).extracting(row -> row.authorFollowedByViewer()).containsExactly(true, true, false);
        assertThat(rows.get(0).commentCount()).isZero();
        assertThat(rows.get(1).commentCount()).isEqualTo(4);
        assertThat(rows.get(1).reactions()).isSameAs(friendReactions);
        verify(comments, times(1)).commentCounts(any());
        verify(reactions, times(1)).summaries(any(), any(), any());
        verify(follows, times(1)).followeesAmong(any(), any());
    }

    @Test
    void theCallersOwnPostIsManageableAndNotFollowedWithNoRoleRead() {
        serve(post(1, caller));

        PostDto row = rows(service.feed(caller, firstPage)).getFirst();

        assertThat(row.authorFollowedByViewer()).isFalse();
        assertThat(row.viewerCanEdit()).isTrue();
        assertThat(row.viewerCanDelete()).isTrue();
        verifyNoInteractions(roles);
    }

    @Test
    void aFollowedUsersPostIsFollowedAndNotManageable() {
        serve(post(1, friend));
        when(follows.followeesAmong(CALLER_ID, List.of(FRIEND_ID))).thenReturn(Set.of(FRIEND_ID));

        PostDto row = rows(service.feed(caller, firstPage)).getFirst();

        assertThat(row.authorFollowedByViewer()).isTrue();
        assertThat(row.viewerCanEdit()).isFalse();
        assertThat(row.viewerCanDelete()).isFalse();
        verifyNoInteractions(roles);
    }

    @Test
    void twoPostsByOneOrganizationCostOneRoleRead() {
        serve(post(1, organization), post(2, organization));
        when(roles.findLiveRole(ORG_ID, CALLER_ID)).thenReturn(Optional.of(OrganizationRole.EDITOR));

        List<PostDto> rows = rows(service.feed(caller, firstPage));

        assertThat(rows).extracting(row -> row.viewerCanEdit()).containsExactly(true, true);
        verify(roles, times(1)).findLiveRole(ORG_ID, CALLER_ID);
    }

    @Test
    void thePageKeepsTheQuerysPositionAndTotal() {
        Pageable thirdPage = PageMapper.toPageable(3, 2);
        Post own = post(1, caller);
        when(posts.findFeed(CALLER_ID, thirdPage)).thenReturn(new PageImpl<>(List.of(own), thirdPage, 5));

        Page<FeedBlockDto> page = service.feed(caller, thirdPage);

        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void anEmptyFeedAnswersAnEmptyPage() {
        when(posts.findFeed(CALLER_ID, firstPage)).thenReturn(Page.empty(firstPage));

        Page<FeedBlockDto> page = service.feed(caller, firstPage);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(roles);
    }

    private void serve(Post... rows) {
        when(posts.findFeed(CALLER_ID, firstPage)).thenReturn(new PageImpl<>(List.of(rows), firstPage, rows.length));
    }

    private static List<PostDto> rows(Page<FeedBlockDto> page) {
        return page.getContent().stream().map(block -> ((FeedBlockDto.PostBlock) block).post()).toList();
    }

    private static Post post(int n, Actor author) {
        Post post = new Post(TestFixtures.user("writer"), author, "Objava " + n);
        ReflectionTestUtils.setField(post, "id", UUID.fromString("00000000-0000-4000-8000-00000000010" + n));
        ReflectionTestUtils.setField(post, "createdAt", Instant.parse("2026-01-02T03:04:05Z"));
        return post;
    }

    private static Organization organization() {
        Organization organization = TestFixtures.organization("acme");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        return organization;
    }
}
