package io.github.matijaradovic.spona.actor.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.dto.FollowActorDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class FollowServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f3");
    private static final Pageable FIRST = PageRequest.of(0, 20);

    private final FollowRepository follows = mock(FollowRepository.class);
    private final ActorRepository actors = mock(ActorRepository.class);
    private final FollowService service = new FollowService(follows, actors);

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @Test
    void followingYourselfIsA422BeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.follow(CALLER_ID, caller))
                .isInstanceOf(UnprocessableException.class);

        verifyNoInteractions(actors, follows);
    }

    @Test
    void followingNobodyIsA404AndWritesNothing() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.follow(SUBJECT_ID, caller)).isInstanceOf(NotFoundException.class);

        verify(follows, never()).insertIfAbsent(any(), any());
    }

    @Test
    void followInsertsTheCallersEdgeIfAbsent() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(true);

        service.follow(SUBJECT_ID, caller);

        verify(follows).insertIfAbsent(CALLER_ID, SUBJECT_ID);
    }

    @Test
    void unfollowingNobodyIsA404() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.unfollow(SUBJECT_ID, caller)).isInstanceOf(NotFoundException.class);

        verify(follows, never()).deleteEdge(any(), any());
    }

    @Test
    void unfollowDeletesTheCallersEdge() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(true);

        service.unfollow(SUBJECT_ID, caller);

        verify(follows).deleteEdge(CALLER_ID, SUBJECT_ID);
    }

    @Test
    void followersOfNobodyIsA404() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.followers(SUBJECT_ID, "", FIRST, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aGuestGetsNoFlagAndCostsNoEdgeQuery() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(true);
        when(follows.findFollowers(SUBJECT_ID, "ana", FIRST))
                .thenReturn(new PageImpl<>(List.of(TestFixtures.userWithId("ana", ROW_ID)), FIRST, 1));

        List<FollowActorDto> rows = service.followers(SUBJECT_ID, "  ana ", FIRST, null).getContent();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.handle()).isEqualTo("ana");
            assertThat(row.isFollowedByViewer()).isNull();
        });
        verify(follows, never()).followeesAmong(any(), any());
    }

    @Test
    void theFlagIsTheCallersOwnEdgeToTheRow() {
        when(actors.existsById(SUBJECT_ID)).thenReturn(true);
        when(follows.findFollowers(SUBJECT_ID, "", FIRST))
                .thenReturn(new PageImpl<>(List.of(TestFixtures.userWithId("ana", ROW_ID)), FIRST, 1));
        when(follows.followeesAmong(CALLER_ID, List.of(ROW_ID))).thenReturn(Set.of(ROW_ID));

        List<FollowActorDto> rows = service.followers(SUBJECT_ID, null, FIRST, caller).getContent();

        assertThat(rows).singleElement().extracting(FollowActorDto::isFollowedByViewer).isEqualTo(true);
    }

    @Test
    void followingOfNobodyIsA404() {
        when(actors.findById(SUBJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.following(SUBJECT_ID, "", FIRST, caller))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void followingOfAnOrganizationIsA404AndRunsNoListQuery() {
        Organization organization = TestFixtures.organization("nordeus");
        when(actors.findById(SUBJECT_ID)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.following(SUBJECT_ID, "", FIRST, caller))
                .isInstanceOf(NotFoundException.class);

        verify(follows, never()).findFollowing(any(), any(), any());
    }
}
