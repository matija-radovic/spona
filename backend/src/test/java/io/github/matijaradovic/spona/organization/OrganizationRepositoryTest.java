package io.github.matijaradovic.spona.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;

class OrganizationRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void roundTripsEveryColumn() {
        Organization organization = TestFixtures.organization("stolarija-novak");
        organization.setDescription("Porodična stolarska radionica.");
        organization.setWebsite("https://novak.rs");
        organization.setAvatarUrl("http://localhost:9000/spona-avatars/novak.webp");
        organization.setLocation("Kragujevac");
        organization.setOtherContactInfo(TestFixtures.contacts());

        UUID id = organizations.saveAndFlush(organization).getId();
        entities.clear();

        Organization loaded = organizations.findById(id).orElseThrow();
        assertThat(loaded.getSlug()).isEqualTo("stolarija-novak");
        assertThat(loaded.getDisplayName()).isEqualTo("Display stolarija-novak");
        assertThat(loaded.getLegalName()).isEqualTo("Legal stolarija-novak d.o.o.");
        assertThat(loaded.getDescription()).isEqualTo("Porodična stolarska radionica.");
        assertThat(loaded.getWebsite()).isEqualTo("https://novak.rs");
        assertThat(loaded.getAvatarUrl()).isEqualTo("http://localhost:9000/spona-avatars/novak.webp");
        assertThat(loaded.getLocation()).isEqualTo("Kragujevac");
        assertThat(loaded.getOtherContactInfo()).containsExactlyElementsOf(TestFixtures.contacts());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getActorType()).isEqualTo(ActorType.ORG);
    }

    @Test
    void writesBothRowsAndTheDiscriminator() {
        UUID id = organizations.saveAndFlush(TestFixtures.organization("pekara-sunce")).getId();
        entities.flush();

        Object actorType = entities.getEntityManager()
                .createNativeQuery("SELECT actor_type FROM actor WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(actorType).isEqualTo("org");
    }

    @Test
    void keepsTheNullableColumnsNull() {
        UUID id = organizations.saveAndFlush(TestFixtures.organization("prazna-firma")).getId();
        entities.clear();

        Organization loaded = organizations.findById(id).orElseThrow();
        assertThat(loaded.getDescription()).isNull();
        assertThat(loaded.getWebsite()).isNull();
        assertThat(loaded.getAvatarUrl()).isNull();
        assertThat(loaded.getLocation()).isNull();
        assertThat(loaded.getOtherContactInfo()).isEmpty();
    }

    @Test
    void findsBySlug() {
        organizations.saveAndFlush(TestFixtures.organization("trazena"));
        entities.clear();

        assertThat(organizations.findBySlug("trazena")).isPresent();
        assertThat(organizations.findBySlug("nepostojeca")).isEmpty();
        assertThat(organizations.existsBySlug("trazena")).isTrue();
        assertThat(organizations.existsBySlug("nepostojeca")).isFalse();
    }

    @Test
    void refusesASecondOrganizationOnTheSameSlug() {
        organizations.saveAndFlush(TestFixtures.organization("duplikat"));
        Organization second = new Organization("duplikat", "Druga", "Druga d.o.o.");

        assertThatThrownBy(() -> organizations.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesASlugThatIsNotOneUrlSegment() {
        Organization organization = new Organization("Velika Firma", "Velika", "Velika d.o.o.");

        assertThatThrownBy(() -> organizations.saveAndFlush(organization))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
