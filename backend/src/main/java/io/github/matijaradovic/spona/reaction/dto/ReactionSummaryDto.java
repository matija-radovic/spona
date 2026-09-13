package io.github.matijaradovic.spona.reaction.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ReactionSummaryDto(
        List<TopEntry> top,
        long othersCount,
        long total,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> viewerReactions) {

    public record TopEntry(String emoji, long count) {
    }

    /** TODO: remove/change when actually implementing the reaction thing */
    public static ReactionSummaryDto empty() {
        return new ReactionSummaryDto(List.of(), 0, 0, null);
    }
}
