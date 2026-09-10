package io.github.matijaradovic.spona.actor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class ActorRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private ActorRepository actors;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void findsEitherSubtypeThroughTheParent() {
        UUID userId = users.saveAndFlush(TestFixtures.user("covek")).getId();
        UUID organizationId = organizations.saveAndFlush(TestFixtures.organization("firma")).getId();
        entities.clear();

        Actor asUser = actors.findById(userId).orElseThrow();
        Actor asOrganization = actors.findById(organizationId).orElseThrow();

        assertThat(asUser).isInstanceOf(User.class);
        assertThat(asUser.getActorType()).isEqualTo(ActorType.USER);
        assertThat(((User) asUser).getUsername()).isEqualTo("covek");

        assertThat(asOrganization).isInstanceOf(Organization.class);
        assertThat(asOrganization.getActorType()).isEqualTo(ActorType.ORG);
        assertThat(((Organization) asOrganization).getSlug()).isEqualTo("firma");
    }

    @Test
    void letsAUserAndAnOrganizationShareAHandle() {
        User user = users.saveAndFlush(TestFixtures.user("spona"));
        Organization organization = organizations.saveAndFlush(TestFixtures.organization("spona"));
        entities.clear();

        assertThat(user.getId()).isNotEqualTo(organization.getId());
        assertThat(users.findByUsername("spona")).isPresent();
        assertThat(organizations.findBySlug("spona")).isPresent();
        assertThat(actors.count()).isEqualTo(2);
    }

    @Test
    void deletingThroughTheParentRemovesTheSubtypeRow() {
        UUID id = users.saveAndFlush(TestFixtures.user("odlazi")).getId();
        entities.flush();

        actors.deleteById(id);
        entities.flush();
        entities.clear();

        assertThat(count("SELECT count(*) FROM actor WHERE id = :id", id)).isZero();
        assertThat(count("SELECT count(*) FROM app_user WHERE id = :id", id)).isZero();
    }

    @Test
    void cascadesFromTheParentRowInTheDatabase() {
        UUID id = organizations.saveAndFlush(TestFixtures.organization("gasi-se")).getId();
        entities.flush();
        entities.clear();

        entities.getEntityManager()
                .createNativeQuery("DELETE FROM actor WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        assertThat(count("SELECT count(*) FROM organization WHERE id = :id", id)).isZero();
    }

    private long count(String sql, UUID id) {
        Object result = entities.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult();
        return ((Number) result).longValue();
    }
}
