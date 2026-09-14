package io.github.matijaradovic.spona.reaction.dto;

import java.util.List;

import io.github.matijaradovic.spona.common.dto.ActorRefDto;

public record ReactionBreakdownDto(List<Entry> entries, long total) {
    public record Entry(String emoji, long count, List<ActorRefDto> reactors) {
    }
}
