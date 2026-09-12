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

class PasswordResetTokenRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Test
    void roundTripsEveryColumn() {
        User owner = user("resetko");
        UUID id = tokens.saveAndFlush(TestFixtures.resetToken(owner, "plaintext")).getId();
        entities.clear();

        PasswordResetToken loaded = tokens.findById(id).orElseThrow();
        assertThat(loaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(loaded.getTokenHash()).isEqualTo(SecureTokens.hash("plaintext"));
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getExpiresAt()).isAfter(loaded.getCreatedAt());
        assertThat(loaded.getConsumedAt()).isNull();
        assertThat(loaded.isLive(Instant.now())).isTrue();
    }

    @Test
    void findsByHashAndNeverByThePlaintext() {
        tokens.saveAndFlush(TestFixtures.resetToken(user("hashko"), "tajna"));
        entities.clear();

        assertThat(tokens.findByTokenHash(SecureTokens.hash("tajna"))).isPresent();
        assertThat(tokens.findByTokenHash("tajna")).isEmpty();
    }

    @Test
    void admitsSeveralLiveRowsForOneUser() {
        User owner = user("viseputa");
        tokens.saveAndFlush(TestFixtures.resetToken(owner, "a"));
        tokens.saveAndFlush(TestFixtures.resetToken(owner, "b"));
        tokens.saveAndFlush(TestFixtures.resetToken(owner, "c"));
        entities.clear();

        assertThat(tokens.findByUserAndConsumedAtIsNull(owner).stream().map(row -> row.getTokenHash()))
                .containsExactlyInAnyOrder(SecureTokens.hash("a"), SecureTokens.hash("b"),
                        SecureTokens.hash("c"));
    }

    @Test
    void theLiveFinderSkipsTheRowsThatWereSpent() {
        User owner = user("potroseno");
        PasswordResetToken spent = TestFixtures.resetToken(owner, "stari");
        spent.consume(Instant.now());
        tokens.saveAndFlush(spent);
        tokens.saveAndFlush(TestFixtures.resetToken(owner, "novi"));
        entities.clear();

        assertThat(tokens.findByUserAndConsumedAtIsNull(owner))
                .singleElement()
                .extracting(row -> row.getTokenHash())
                .isEqualTo(SecureTokens.hash("novi"));
    }

    @Test
    void theLiveFinderSeesOnlyTheOwnersRows() {
        User owner = user("svoje");
        tokens.saveAndFlush(TestFixtures.resetToken(owner, "moj"));
        tokens.saveAndFlush(TestFixtures.resetToken(user("tudje"), "tvoj"));
        entities.clear();

        assertThat(tokens.findByUserAndConsumedAtIsNull(owner)).hasSize(1);
    }

    @Test
    void refusesTwoRowsCarryingTheSameDigest() {
        String hash = SecureTokens.hash("sudar");
        tokens.saveAndFlush(new PasswordResetToken(user("prvi"), hash,
                Instant.now().plus(PasswordResetToken.LIFETIME)));

        PasswordResetToken clash = new PasswordResetToken(user("drugi"), hash,
                Instant.now().plus(PasswordResetToken.LIFETIME));

        assertThatThrownBy(() -> tokens.saveAndFlush(clash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAnExpiryThatDoesNotOutliveTheRow() {
        PasswordResetToken backwards = new PasswordResetToken(user("prosli"),
                SecureTokens.hash("a"), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> tokens.saveAndFlush(backwards))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String username) {
        return users.saveAndFlush(TestFixtures.user(username));
    }
}
