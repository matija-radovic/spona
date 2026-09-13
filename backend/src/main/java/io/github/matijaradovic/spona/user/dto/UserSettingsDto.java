package io.github.matijaradovic.spona.user.dto;

import java.util.List;
import java.util.UUID;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.user.User;

/** The account as its owner edits it */
public record UserSettingsDto(
        UUID id,
        String username,
        String email,
        String fullName,
        String bio,
        String avatarUrl,
        String location,
        List<ContactEntry> otherContactInfo,
        PrivacyDto privacy) {

    public static UserSettingsDto from(User user, PrivacyDto privacy) {
        return new UserSettingsDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getLocation(),
                List.copyOf(user.getOtherContactInfo()),
                privacy);
    }
}
