package io.github.matijaradovic.spona.actor.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.support.AbstractDataJpaTest;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class FollowRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private FollowRepository follows;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void countsHowManyFollowAnActor() {
        User subject = users.save(TestFixtures.user("followsubject"));
        follows.save(new Follow(users.save(TestFixtures.user("followerone")), subject));
        follows.save(new Follow(users.save(TestFixtures.user("followertwo")), subject));

        assertThat(follows.countByFollowee_Id(subject.getId())).isEqualTo(2);
        assertThat(follows.countByFollower_Id(subject.getId())).isZero();
    }

    @Test
    void countsHowManyAPersonFollows() {
        User viewer = users.save(TestFixtures.user("followviewer"));
        follows.save(new Follow(viewer, users.save(TestFixtures.user("followtargetone"))));
        follows.save(new Follow(viewer, organizations.save(TestFixtures.organization("follow-org"))));

        assertThat(follows.countByFollower_Id(viewer.getId())).isEqualTo(2);
    }

    @Test
    void answersAnActorNobodyFollowsWithZero() {
        User lonely = users.save(TestFixtures.user("followlonely"));

        assertThat(follows.countByFollowee_Id(lonely.getId())).isZero();
        assertThat(follows.countByFollower_Id(lonely.getId())).isZero();
    }

    @Test
    void reportsTheEdgeOnlyInTheDirectionItWasWritten() {
        User viewer = users.save(TestFixtures.user("followedgeviewer"));
        User subject = users.save(TestFixtures.user("followedgesubject"));
        follows.save(new Follow(viewer, subject));

        assertThat(follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), subject.getId()))
                .isTrue();
        assertThat(follows.existsByFollower_IdAndFollowee_Id(subject.getId(), viewer.getId()))
                .isFalse();
    }

    @Test
    void reportsTheEdgeToAnOrganizationToo() {
        User viewer = users.save(TestFixtures.user("followorgviewer"));
        Organization subject = organizations.save(TestFixtures.organization("followed-org"));
        follows.save(new Follow(viewer, subject));

        assertThat(follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), subject.getId()))
                .isTrue();
    }

    @Test
    void refusesAPersonFollowingThemselves() {
        User lonely = users.save(TestFixtures.user("followself"));

        assertThatThrownBy(() -> follows.saveAndFlush(new Follow(lonely, lonely)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
