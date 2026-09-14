package io.github.matijaradovic.spona.reaction.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ReactionSummaryDto(
        List<TopEntry> top,
        long othersCount,
        long total,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> viewerReactions) {

    public static final int TOP_MAX = 3;

    public record TopEntry(String emoji, long count) {
    }

    public static ReactionSummaryDto of(List<TopEntry> entries, List<String> viewerReactions) {
        List<TopEntry> top = List.copyOf(entries.subList(0, Math.min(TOP_MAX, entries.size())));
        long total = entries.stream().mapToLong(entry -> entry.count()).sum();
        long inTop = top.stream().mapToLong(entry -> entry.count()).sum();
        return new ReactionSummaryDto(top, total - inTop, total, viewerReactions);
    }
}
