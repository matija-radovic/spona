package io.github.matijaradovic.spona.comment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

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

    private static Map<UUID, Long> tally(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
