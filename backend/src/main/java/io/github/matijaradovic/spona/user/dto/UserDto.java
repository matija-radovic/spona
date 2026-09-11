package io.github.matijaradovic.spona.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.user.User;

/** The signed-in user's own account */
public record UserDto(
        UUID id,
        ActorType actorType,
        Instant createdAt,
        String username,
        String fullName,
        String email,
        String bio,
        String avatarUrl,
        String location,
        List<ContactEntry> otherContactInfo) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getActorType(),
                user.getCreatedAt(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getLocation(),
                List.copyOf(user.getOtherContactInfo()));
    }
}
