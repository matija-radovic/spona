package io.github.matijaradovic.spona.actor.follow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.user.User;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    long countByFollowee_Id(UUID followeeId);

    long countByFollower_Id(UUID followerId);

    boolean existsByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);

    @Modifying
    @Query(value = "insert into follows (follower_id, followee_id) values (:followerId, :followeeId)"
            + " on conflict do nothing", nativeQuery = true)
    void insertIfAbsent(UUID followerId, UUID followeeId);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followee.id = :followeeId")
    void deleteEdge(UUID followerId, UUID followeeId);

    /** Newest edge first >> {@code q} matches name or handle, case-folded, and '' matches everyone */
    @Query("select u from Follow f join f.follower u where f.followee.id = :actorId"
            + " and (locate(lower(:q), lower(u.fullName)) > 0 or locate(lower(:q), lower(u.username)) > 0)"
            + " order by f.createdAt desc, u.id")
    Page<User> findFollowers(UUID actorId, String q, Pageable pageable);

    @Query("select a from Follow f join f.followee a where f.follower.id = :actorId"
            + " and (exists (select u.id from User u where u.id = a.id"
            + " and (locate(lower(:q), lower(u.fullName)) > 0 or locate(lower(:q), lower(u.username)) > 0))"
            + " or exists (select o.id from Organization o where o.id = a.id"
            + " and (locate(lower(:q), lower(o.displayName)) > 0 or locate(lower(:q), lower(o.slug)) > 0)))"
            + " order by f.createdAt desc, a.id")
    Page<Actor> findFollowing(UUID actorId, String q, Pageable pageable);

    @Query("select f.followee.id from Follow f where f.follower.id = :followerId and f.followee.id in :followeeIds")
    List<UUID> findFolloweeIds(UUID followerId, Collection<UUID> followeeIds);

    @Query("select f.followee.id, count(f) from Follow f where f.followee.id in :ids group by f.followee.id")
    List<Object[]> countFollowersGrouped(Collection<UUID> ids);

    @Query("select f.follower.id, count(f) from Follow f where f.follower.id in :ids group by f.follower.id")
    List<Object[]> countFollowingGrouped(Collection<UUID> ids);

    default Set<UUID> followeesAmong(UUID followerId, Collection<UUID> ids) {
        return ids.isEmpty() ? Set.of() : Set.copyOf(findFolloweeIds(followerId, ids));
    }

    default Map<UUID, Long> followerCounts(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : tally(countFollowersGrouped(ids));
    }

    default Map<UUID, Long> followingCounts(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : tally(countFollowingGrouped(ids));
    }

    private static Map<UUID, Long> tally(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
