package io.github.matijaradovic.spona.reaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.comment.CommentRepository;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.reaction.dto.ReactionBreakdownDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto.TopEntry;
import io.github.matijaradovic.spona.user.User;

@Service
public class ReactionService {

    private static final Comparator<TopEntry> BY_COUNT_THEN_PALETTE = Comparator
            .comparingLong((TopEntry entry) -> entry.count()).reversed()
            .thenComparingInt(entry -> ReactionPalette.indexOf(entry.emoji()));

    private final ReactionRepository reactions;
    private final PostRepository posts;
    private final CommentRepository comments;

    public ReactionService(ReactionRepository reactions, PostRepository posts, CommentRepository comments) {
        this.reactions = reactions;
        this.posts = posts;
        this.comments = comments;
    }

    @Transactional
    public void react(String targetType, UUID targetId, User caller, String emoji) {
        ReactionTargetType type = targetType(targetType);
        String checked = checkedEmoji(emoji);
        requireTarget(type, targetId);
        reactions.insertIfAbsent(type.getId(), targetId, caller.getId(), checked);
    }

    @Transactional
    public void unreact(String targetType, UUID targetId, User caller, String emoji) {
        ReactionTargetType type = targetType(targetType);
        String checked = checkedEmoji(emoji);
        requireTarget(type, targetId);
        reactions.deleteOne(type.getId(), targetId, caller.getId(), checked);
    }

    @Transactional(readOnly = true)
    public ReactionBreakdownDto breakdown(String targetType, UUID targetId) {
        ReactionTargetType type = targetType(targetType);
        requireTarget(type, targetId);
        Map<String, List<ActorRefDto>> reactorsByEmoji = new LinkedHashMap<>();
        for (Reaction reaction : reactions.findWithReactors(type.getId(), targetId)) {
            reactorsByEmoji.computeIfAbsent(reaction.getEmoji(), emoji -> new ArrayList<>())
                    .add(ActorRefDto.from(reaction.getCreatedBy()));
        }
        List<ReactionBreakdownDto.Entry> entries = reactorsByEmoji.entrySet().stream()
                .map(entry -> new ReactionBreakdownDto.Entry(entry.getKey(), entry.getValue().size(),
                        List.copyOf(entry.getValue())))
                .sorted(Comparator.comparingLong((ReactionBreakdownDto.Entry entry) -> entry.count()).reversed()
                        .thenComparingInt(entry -> ReactionPalette.indexOf(entry.emoji())))
                .toList();
        long total = entries.stream().mapToLong(entry -> entry.count()).sum();
        return new ReactionBreakdownDto(entries, total);
    }

    /** One summary per target id, read in two queries whatever the number of targets. */
    public Map<UUID, ReactionSummaryDto> summaries(ReactionTargetType type, Collection<UUID> targetIds, User viewer) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TopEntry>> counts = new HashMap<>();
        for (Object[] row : reactions.countGrouped(type.getId(), targetIds)) {
            counts.computeIfAbsent((UUID) row[0], id -> new ArrayList<>())
                    .add(new TopEntry((String) row[1], (Long) row[2]));
        }
        Map<UUID, List<String>> viewerEmojis = new HashMap<>();
        if (viewer != null) {
            for (Object[] row : reactions.findEmojisBy(type.getId(), targetIds, viewer.getId())) {
                viewerEmojis.computeIfAbsent((UUID) row[0], id -> new ArrayList<>()).add((String) row[1]);
            }
        }
        Map<UUID, ReactionSummaryDto> summaries = new HashMap<>();
        for (UUID targetId : targetIds) {
            List<TopEntry> entries = counts.getOrDefault(targetId, List.of()).stream()
                    .sorted(BY_COUNT_THEN_PALETTE)
                    .toList();
            List<String> viewerReactions = viewer == null ? null : viewerEmojis.getOrDefault(targetId, List.of())
                    .stream()
                    .sorted(Comparator.comparingInt(emoji -> ReactionPalette.indexOf(emoji)))
                    .toList();
            summaries.put(targetId, ReactionSummaryDto.of(entries, viewerReactions));
        }
        return summaries;
    }

    public ReactionSummaryDto summary(ReactionTargetType type, UUID targetId, User viewer) {
        return summaries(type, List.of(targetId), viewer).get(targetId);
    }

    private static ReactionTargetType targetType(String segment) {
        return ReactionTargetType.fromSegment(segment)
                .orElseThrow(() -> new NotFoundException("Unknown reaction target type."));
    }

    private static String checkedEmoji(String emoji) {
        if (emoji == null || !ReactionPalette.contains(emoji)) {
            throw UnprocessableException.on("emoji", "Emoji must be one of the reaction palette.");
        }
        return emoji;
    }

    private void requireTarget(ReactionTargetType type, UUID targetId) {
        boolean exists = switch (type) {
            case POST -> posts.existsById(targetId);
            case COMMENT -> comments.existsById(targetId);
        };
        if (!exists) {
            throw new NotFoundException(type == ReactionTargetType.POST ? "Post not found." : "Comment not found.");
        }
    }
}
