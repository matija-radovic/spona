package io.github.matijaradovic.spona.reaction;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public enum ReactionTargetType {

    POST("post", UUID.fromString("11111111-1111-4111-8111-111111111111")),
    COMMENT("comment", UUID.fromString("22222222-2222-4222-8222-222222222222"));

    private final String segment;
    private final UUID id;

    ReactionTargetType(String segment, UUID id) {
        this.segment = segment;
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static Optional<ReactionTargetType> fromSegment(String segment) {
        return Arrays.stream(values()).filter(type -> type.segment.equals(segment)).findFirst();
    }
}
