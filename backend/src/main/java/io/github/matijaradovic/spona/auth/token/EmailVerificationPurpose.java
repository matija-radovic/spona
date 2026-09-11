package io.github.matijaradovic.spona.auth.token;

import java.time.Duration;

public enum EmailVerificationPurpose {

    SIGNUP(Duration.ofHours(24)),
    CHANGE(Duration.ofMinutes(60));

    private final Duration lifetime;

    EmailVerificationPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    /** where lifetime is written */
    public Duration lifetime() {
        return lifetime;
    }
}
