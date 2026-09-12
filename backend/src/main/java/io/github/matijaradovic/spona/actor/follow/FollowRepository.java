package io.github.matijaradovic.spona.actor.follow;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    long countByFollowee_Id(UUID followeeId);

    long countByFollower_Id(UUID followerId);

    boolean existsByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);
}
