package io.github.matijaradovic.spona.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import io.github.matijaradovic.spona.support.NamedEnumProbe.ProbePurpose;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class NamedEnumMappingTest extends AbstractDataJpaTest {

    private static final String HASH_A = "a".repeat(63) + "1";
    private static final String HASH_B = "b".repeat(63) + "2";

    @Autowired
    private TestEntityManager entities;

    @Autowired
    private NamedEnumProbeRepository probes;

    @Autowired
    private UserRepository users;

    private UUID userId() {
        User user = users.saveAndFlush(TestFixtures.user("prober"));
        return user.getId();
    }

    @Test
    void writesAndReadsBackANativeEnum() {
        UUID owner = userId();

        UUID id = probes
                .saveAndFlush(new NamedEnumProbe(owner, ProbePurpose.SIGNUP, "prober@example.com", HASH_A))
                .getId();
        entities.clear();

        NamedEnumProbe loaded = probes.findById(id).orElseThrow();
        assertThat(loaded.getPurpose()).isEqualTo(ProbePurpose.SIGNUP);
        assertThat(loaded.getTokenHash()).isEqualTo(HASH_A);
    }

    /** The direction that actually breaks, a Java value bound into a WHERE */
    @Test
    void bindsANativeEnumAsAQueryParameter() {
        UUID owner = userId();
        probes.save(new NamedEnumProbe(owner, ProbePurpose.SIGNUP, "prober@example.com", HASH_A));
        probes.save(new NamedEnumProbe(owner, ProbePurpose.CHANGE, "new@example.com", HASH_B));
        probes.flush();
        entities.clear();

        List<NamedEnumProbe> found = probes.findByPurpose(ProbePurpose.CHANGE);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getEmail()).isEqualTo("new@example.com");
    }

    /**
     * The column is a real `email_verification_purpose` and not text: `::text`
     * only compiles against an enum, and the label is what came out.
     */
    @Test
    void storesTheEnumLabelInTheNativeType() {
        UUID owner = userId();
        UUID id = probes
                .saveAndFlush(new NamedEnumProbe(owner, ProbePurpose.CHANGE, "new@example.com", HASH_B))
                .getId();
        entities.flush();

        Object label = entities.getEntityManager()
                .createNativeQuery("SELECT purpose::text FROM email_verification_tokens WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(label).isEqualTo("CHANGE");
    }
}
