package io.github.matijaradovic.spona.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class EmailVerificationTokenRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Test
    void roundTripsEveryColumn() {
        User owner = user("tokenko");
        UUID id = tokens.saveAndFlush(
                TestFixtures.token(owner, EmailVerificationPurpose.CHANGE, "novi@example.com", "plaintext"))
                .getId();
        entities.clear();

        EmailVerificationToken loaded = tokens.findById(id).orElseThrow();
        assertThat(loaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(loaded.getPurpose()).isEqualTo(EmailVerificationPurpose.CHANGE);
        assertThat(loaded.getEmail()).isEqualTo("novi@example.com");
        assertThat(loaded.getTokenHash()).isEqualTo(SecureTokens.hash("plaintext"));
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getExpiresAt()).isAfter(loaded.getCreatedAt());
        assertThat(loaded.getConsumedAt()).isNull();
        assertThat(loaded.isLive(Instant.now())).isTrue();
    }

    /** The column is a real `email_verification_purpose`: `::text` compiles only against an enum. */
    @Test
    void storesThePurposeInTheNativeEnumType() {
        UUID id = tokens.saveAndFlush(
                TestFixtures.token(user("nativko"), EmailVerificationPurpose.SIGNUP, "nativko@example.com", "p"))
                .getId();
        entities.flush();

        Object label = entities.getEntityManager()
                .createNativeQuery("SELECT purpose::text FROM email_verification_tokens WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(label).isEqualTo("SIGNUP");
    }

    @Test
    void findsByHashAndNeverByThePlaintext() {
        tokens.saveAndFlush(
                TestFixtures.token(user("hashko"), EmailVerificationPurpose.SIGNUP, "hashko@example.com", "tajna"));
        entities.clear();

        assertThat(tokens.findByTokenHash(SecureTokens.hash("tajna"))).isPresent();
        assertThat(tokens.findByTokenHash(SecureTokens.hash("nesto-drugo"))).isEmpty();
    }

    @Test
    void findsOnlyTheLiveRowForAUserAndPurpose() {
        User owner = user("zivko");
        EmailVerificationToken spent =
                TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "zivko@example.com", "stari");
        spent.consume(Instant.now());
        tokens.saveAndFlush(spent);
        entities.clear();

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(owner, EmailVerificationPurpose.SIGNUP))
                .isEmpty();

        tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "zivko@example.com", "novi"));
        entities.clear();

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(owner, EmailVerificationPurpose.SIGNUP))
                .isPresent();
    }

    @Test
    void refusesASecondLiveRowForTheSameUserAndPurpose() {
        User owner = user("duplikat");
        tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "duplikat@example.com", "a"));

        EmailVerificationToken second =
                TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "duplikat@example.com", "b");

        assertThatThrownBy(() -> tokens.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void admitsTheNextRowOnceThePreviousIsConsumed() {
        User owner = user("resendko");
        EmailVerificationToken first =
                tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "resendko@example.com", "a"));

        first.consume(Instant.now());
        tokens.flush();
        tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "resendko@example.com", "b"));
        entities.clear();

        assertThat(tokens.findAll()).hasSize(2);
        assertThat(tokens.findByTokenHash(SecureTokens.hash("b")).orElseThrow().getConsumedAt()).isNull();
    }

    @Test
    void admitsOneLiveRowForEachPurpose() {
        User owner = user("oba");
        tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.SIGNUP, "oba@example.com", "a"));
        tokens.saveAndFlush(TestFixtures.token(owner, EmailVerificationPurpose.CHANGE, "novi@example.com", "b"));
        entities.clear();

        assertThat(tokens.findAll()).hasSize(2);
    }

    @Test
    void refusesAnExpiryThatDoesNotOutliveTheRow() {
        EmailVerificationToken backwards = new EmailVerificationToken(user("prosli"),
                EmailVerificationPurpose.SIGNUP, "prosli@example.com", SecureTokens.hash("a"),
                Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> tokens.saveAndFlush(backwards))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String username) {
        return users.saveAndFlush(TestFixtures.user(username));
    }
}
