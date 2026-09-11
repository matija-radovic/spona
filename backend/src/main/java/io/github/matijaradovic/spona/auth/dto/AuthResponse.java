package io.github.matijaradovic.spona.auth.dto;

import io.github.matijaradovic.spona.user.dto.UserDto;

public record AuthResponse(String token, UserDto user) {
}
