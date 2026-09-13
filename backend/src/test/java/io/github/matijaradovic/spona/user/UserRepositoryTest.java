package io.github.matijaradovic.spona.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;

class UserRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private UserRepository users;

    @Test
    void roundTripsEveryColumn() {
        User user = TestFixtures.user("marko");
        user.setFullName("Marko Marković");
        user.setBio("Radim sa drvetom.");
        user.setAvatarUrl("http://localhost:9000/spona-avatars/marko.png");
        user.setLocation("Novi Sad");
        user.setEmailVerifiedAt(Instant.parse("2026-01-01T10:15:30Z"));
        user.setOtherContactInfo(TestFixtures.contacts());

        UUID id = users.saveAndFlush(user).getId();
        entities.clear();

        User loaded = users.findById(id).orElseThrow();
        assertThat(loaded.getUsername()).isEqualTo("marko");
        assertThat(loaded.getFullName()).isEqualTo("Marko Marković");
        assertThat(loaded.getEmail()).isEqualTo("marko@example.com");
        assertThat(loaded.getPasswordDigest()).isEqualTo("digest-marko");
        assertThat(loaded.getEmailVerifiedAt()).isEqualTo(Instant.parse("2026-01-01T10:15:30Z"));
        assertThat(loaded.getBio()).isEqualTo("Radim sa drvetom.");
        assertThat(loaded.getAvatarUrl()).isEqualTo("http://localhost:9000/spona-avatars/marko.png");
        assertThat(loaded.getLocation()).isEqualTo("Novi Sad");
        assertThat(loaded.getOtherContactInfo()).containsExactlyElementsOf(TestFixtures.contacts());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getActorType()).isEqualTo(ActorType.USER);
    }

    /** JOINED writes two rows, and the parent carries the discriminator. */
    @Test
    void writesBothRowsAndTheDiscriminator() {
        UUID id = users.saveAndFlush(TestFixtures.user("jelena")).getId();
        entities.flush();

        assertThat(scalar("SELECT actor_type FROM actor WHERE id = :id", id)).isEqualTo("user");
        assertThat(count("SELECT count(*) FROM app_user WHERE id = :id", id)).isEqualTo(1);
    }

    /** The Personal stage of registration is skippable, so '' is a stored value. */
    @Test
    void storesAnEmptyFullName() {
        UUID id = users.saveAndFlush(TestFixtures.user("nenad")).getId();
        entities.clear();

        assertThat(users.findById(id).orElseThrow().getFullName()).isEmpty();
    }

    /** The three nullable text columns have two ways to say nothing, and only
     *  NULL is the one every reader tests for. */
    @Test
    void keepsTheNullableColumnsNull() {
        UUID id = users.saveAndFlush(TestFixtures.user("stefan")).getId();
        entities.clear();

        User loaded = users.findById(id).orElseThrow();
        assertThat(loaded.getBio()).isNull();
        assertThat(loaded.getAvatarUrl()).isNull();
        assertThat(loaded.getLocation()).isNull();
        assertThat(loaded.getEmailVerifiedAt()).isNull();
        assertThat(loaded.getOtherContactInfo()).isEmpty();
    }

    @Test
    void refusesAnEmptyBio() {
        User user = TestFixtures.user("ivana");
        user.setBio("");

        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByUsername() {
        users.saveAndFlush(TestFixtures.user("milica"));
        entities.clear();

        assertThat(users.findByUsername("milica")).isPresent();
        assertThat(users.findByUsername("nepostojeci")).isEmpty();
        assertThat(users.existsByUsername("milica")).isTrue();
        assertThat(users.existsByUsername("nepostojeci")).isFalse();
    }

    /** The unique index is on `lower(email)`, so the lookup has to be too. */
    @Test
    void findsByEmailWhateverTheCase() {
        users.saveAndFlush(TestFixtures.user("dragan"));
        entities.clear();

        assertThat(users.findByEmailIgnoreCase("DRAGAN@EXAMPLE.COM")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("Dragan@Example.Com")).isTrue();
    }

    @Test
    void refusesASecondUserOnTheSameHandle() {
        users.saveAndFlush(TestFixtures.user("dupli"));
        User second = new User("dupli", "drugi@example.com", "digest");

        assertThatThrownBy(() -> users.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** The JSONB envelope is checked for shape as well as length, so an entry
     *  with an over-long description is refused rather than stored. */
    @Test
    void refusesAnOverLongContactDescription() {
        User user = TestFixtures.user("kontakt");
        user.setOtherContactInfo(List.of(new ContactEntry("email", "d".repeat(33), "office@example.com")));

        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Object scalar(String sql, UUID id) {
        return entities.getEntityManager().createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    private long count(String sql, UUID id) {
        return ((Number) scalar(sql, id)).longValue();
    }
}
