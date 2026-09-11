package io.github.matijaradovic.spona.auth.token;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.matijaradovic.spona.user.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /** `verification_token_hash_key` is unique, so the plaintext is never a lookup key. */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findByUserAndPurposeAndConsumedAtIsNull(
            User user, EmailVerificationPurpose purpose);
}
