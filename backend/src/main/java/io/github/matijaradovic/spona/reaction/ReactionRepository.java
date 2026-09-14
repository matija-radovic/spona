package io.github.matijaradovic.spona.reaction;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    @Modifying
    @Query(value = "insert into reaction (id, emoji, target_type_id, target_id, created_by_id)"
            + " values (gen_random_uuid(), :emoji, :targetTypeId, :targetId, :userId) on conflict do nothing",
            nativeQuery = true)
    int insertIfAbsent(UUID targetTypeId, UUID targetId, UUID userId, String emoji);

    @Modifying
    @Query("delete from Reaction r where r.targetTypeId = :targetTypeId and r.targetId = :targetId"
            + " and r.createdBy.id = :userId and r.emoji = :emoji")
    int deleteOne(UUID targetTypeId, UUID targetId, UUID userId, String emoji);

    @Query("select r from Reaction r join fetch r.createdBy u"
            + " where r.targetTypeId = :targetTypeId and r.targetId = :targetId order by u.username")
    List<Reaction> findWithReactors(UUID targetTypeId, UUID targetId);

    @Query("select r.targetId, r.emoji, count(r) from Reaction r"
            + " where r.targetTypeId = :targetTypeId and r.targetId in :targetIds group by r.targetId, r.emoji")
    List<Object[]> countGrouped(UUID targetTypeId, Collection<UUID> targetIds);

    @Query("select r.targetId, r.emoji from Reaction r"
            + " where r.targetTypeId = :targetTypeId and r.targetId in :targetIds and r.createdBy.id = :userId")
    List<Object[]> findEmojisBy(UUID targetTypeId, Collection<UUID> targetIds, UUID userId);
}
