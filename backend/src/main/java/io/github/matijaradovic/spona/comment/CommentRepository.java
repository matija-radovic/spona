package io.github.matijaradovic.spona.comment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Below this many reactions a root comment scores 0 under the top sort */
    int TOP_CLAMP = 3;

    String RANKED_ROOTS = "with ranked as (select c.id, c.created_at,"
            + " case when count(r.id) >= " + TOP_CLAMP + " then count(r.id) else 0 end as score"
            + " from comment c left join reaction r on r.target_id = c.id and r.target_type_id ="
            + " (select target_type_id from reaction_target_types where table_name = 'comments')"
            + " where c.part_of_post_id = :postId and c.response_to_comment_id is null group by c.id) ";

    @Query("select c from Comment c join fetch c.createdFor where c.id = :commentId")
    Optional<Comment> findWithCreatedFor(UUID commentId);

    @Query("select c from Comment c join fetch c.createdFor where c.id in :commentIds")
    List<Comment> findAllWithCreatedFor(Collection<UUID> commentIds);

    @Query("select c.responseToComment.id from Comment c where c.id = :commentId")
    Optional<UUID> findParentId(UUID commentId);

    @Query(value = "select c from Comment c join fetch c.createdFor"
            + " where c.post.id = :postId and c.responseToComment is null and c.id <> :withheldId",
            countQuery = "select count(c) from Comment c"
            + " where c.post.id = :postId and c.responseToComment is null and c.id <> :withheldId")
    Page<Comment> findRoots(UUID postId, UUID withheldId, Pageable pageable);

    @Query("select count(c) from Comment c"
            + " where c.post.id = :postId and c.responseToComment is null and c.id <> :withheldId")
    long countRoots(UUID postId, UUID withheldId);

    @Query(value = RANKED_ROOTS + "select id from ranked where id <> :withheldId"
            + " order by score desc, created_at, id limit :limit offset :offset", nativeQuery = true)
    List<UUID> findTopRootIds(UUID postId, UUID withheldId, int limit, long offset);

    @Query(value = RANKED_ROOTS + "select count(*) from ranked o join ranked a on a.id = :commentId"
            + " where o.score > a.score or (o.score = a.score and (o.created_at, o.id) < (a.created_at, a.id))",
            nativeQuery = true)
    long countRootsRankedBefore(UUID postId, UUID commentId);

    @Query(value = "select c from Comment c join fetch c.createdFor"
            + " where c.responseToComment.id = :parentId order by c.createdAt, c.id",
            countQuery = "select count(c) from Comment c where c.responseToComment.id = :parentId")
    Page<Comment> findResponses(UUID parentId, Pageable pageable);

    @Query(value = "select count(*) from comment c join comment a on a.id = :commentId"
            + " where c.response_to_comment_id = a.response_to_comment_id"
            + " and (c.created_at, c.id) < (a.created_at, a.id)", nativeQuery = true)
    long countResponsesBefore(UUID commentId);

    @Query("select c from Comment c where c.createdFor.id = :actorId order by c.createdAt desc, c.id desc")
    Page<Comment> findByCreatedFor(UUID actorId, Pageable pageable);

    @Query("select c.post.id, count(c) from Comment c where c.post.id in :postIds group by c.post.id")
    List<Object[]> countByPostIdsGrouped(Collection<UUID> postIds);

    @Query("select c.responseToComment.id, count(c) from Comment c"
            + " where c.responseToComment.id in :parentIds group by c.responseToComment.id")
    List<Object[]> countRepliesGrouped(Collection<UUID> parentIds);

    default Map<UUID, Long> commentCounts(Collection<UUID> postIds) {
        return postIds.isEmpty() ? Map.of() : tally(countByPostIdsGrouped(postIds));
    }

    default Map<UUID, Long> replyCounts(Collection<UUID> parentIds) {
        return parentIds.isEmpty() ? Map.of() : tally(countRepliesGrouped(parentIds));
    }

    default Page<Comment> findTopRoots(UUID postId, UUID withheldId, Pageable pageable) {
        List<UUID> ids = findTopRootIds(postId, withheldId, pageable.getPageSize(), pageable.getOffset());
        Map<UUID, Comment> byId = ids.isEmpty() ? Map.of() : findAllWithCreatedFor(ids).stream()
                .collect(Collectors.toMap(comment -> comment.getId(), comment -> comment));
        return new PageImpl<>(ids.stream().map(byId::get).toList(), pageable, countRoots(postId, withheldId));
    }

    private static Map<UUID, Long> tally(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
