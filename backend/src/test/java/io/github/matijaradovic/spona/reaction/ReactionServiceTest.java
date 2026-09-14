package io.github.matijaradovic.spona.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.reaction.dto.ReactionBreakdownDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto.TopEntry;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ReactionServiceTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b3");
    private static final UUID QUIET_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b4");
    private static final UUID POST_TYPE = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final ReactionRepository reactions = mock(ReactionRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final ReactionService service = new ReactionService(reactions, posts, comments);

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);
    private final User other = TestFixtures.userWithId("other", OTHER_ID);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "🦄", "e1", "❤"})
    void anEmojiOutsideThePaletteIsRefusedBeforeAnyRead(String emoji) {
        assertThatThrownBy(() -> service.react("post", TARGET_ID, caller, emoji))
                .isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> service.unreact("post", TARGET_ID, caller, emoji))
                .isInstanceOf(UnprocessableException.class);
        verifyNoInteractions(reactions, posts, comments);
    }

    @Test
    void anUnknownTargetTypeIs404() {
        assertThatThrownBy(() -> service.react("job", TARGET_ID, caller, "👍")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.breakdown("posts", TARGET_ID)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(reactions, posts, comments);
    }

    @Test
    void aTargetThatDoesNotExistIs404() {
        when(comments.existsById(TARGET_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.react("comment", TARGET_ID, caller, "👍"))
                .isInstanceOf(NotFoundException.class);
        verify(reactions, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    void reactAndUnreactWriteUnderTheSeededTypeId() {
        when(posts.existsById(TARGET_ID)).thenReturn(true);

        service.react("post", TARGET_ID, caller, "❤️");
        service.unreact("post", TARGET_ID, caller, "❤️");

        verify(reactions).insertIfAbsent(POST_TYPE, TARGET_ID, CALLER_ID, "❤️");
        verify(reactions).deleteOne(POST_TYPE, TARGET_ID, CALLER_ID, "❤️");
    }

    @Test
    void aSummaryClampsTopToThreeAndBreaksTiesByPalette() {
        when(reactions.countGrouped(eq(POST_TYPE), anyCollection())).thenReturn(List.of(
                new Object[] {TARGET_ID, "😂", 2L},
                new Object[] {TARGET_ID, "👍", 2L},
                new Object[] {TARGET_ID, "🔥", 5L},
                new Object[] {TARGET_ID, "❤️", 1L}));
        when(reactions.findEmojisBy(eq(POST_TYPE), anyCollection(), eq(CALLER_ID))).thenReturn(List.of(
                new Object[] {TARGET_ID, "😂"},
                new Object[] {TARGET_ID, "👍"}));

        Map<UUID, ReactionSummaryDto> summaries = service.summaries(ReactionTargetType.POST,
                List.of(TARGET_ID, QUIET_ID), caller);

        ReactionSummaryDto busy = summaries.get(TARGET_ID);
        assertThat(busy.top()).containsExactly(new TopEntry("🔥", 5), new TopEntry("👍", 2), new TopEntry("😂", 2));
        assertThat(busy.othersCount()).isEqualTo(1);
        assertThat(busy.total()).isEqualTo(10);
        assertThat(busy.viewerReactions()).containsExactly("👍", "😂");

        ReactionSummaryDto quiet = summaries.get(QUIET_ID);
        assertThat(quiet.top()).isEmpty();
        assertThat(quiet.total()).isZero();
        assertThat(quiet.viewerReactions()).isEmpty();
    }

    @Test
    void aGuestSummaryHasNoViewerReactions() {
        ReactionSummaryDto summary = service.summary(ReactionTargetType.POST, TARGET_ID, null);

        assertThat(summary.viewerReactions()).isNull();
        verify(reactions, never()).findEmojisBy(any(), anyCollection(), any());
    }

    @Test
    void noTargetsReadNothing() {
        assertThat(service.summaries(ReactionTargetType.COMMENT, List.of(), caller)).isEmpty();
        verifyNoInteractions(reactions);
    }

    @Test
    void theBreakdownIsOrderedByCountThenPaletteAndUnclamped() {
        when(posts.existsById(TARGET_ID)).thenReturn(true);
        when(reactions.findWithReactors(POST_TYPE, TARGET_ID)).thenReturn(List.of(
                new Reaction(ReactionTargetType.POST, TARGET_ID, caller, "😂"),
                new Reaction(ReactionTargetType.POST, TARGET_ID, caller, "🔥"),
                new Reaction(ReactionTargetType.POST, TARGET_ID, caller, "🙏"),
                new Reaction(ReactionTargetType.POST, TARGET_ID, caller, "👍"),
                new Reaction(ReactionTargetType.POST, TARGET_ID, other, "🔥")));

        ReactionBreakdownDto breakdown = service.breakdown("post", TARGET_ID);

        assertThat(breakdown.entries()).extracting(entry -> entry.emoji()).containsExactly("🔥", "👍", "😂", "🙏");
        assertThat(breakdown.entries().getFirst().count()).isEqualTo(2);
        assertThat(breakdown.entries().getFirst().reactors()).extracting(actor -> actor.handle())
                .containsExactly("caller", "other");
        assertThat(breakdown.total()).isEqualTo(5);
    }
}
