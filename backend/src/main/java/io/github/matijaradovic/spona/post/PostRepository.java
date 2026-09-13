package io.github.matijaradovic.spona.post;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("select p from Post p join fetch p.createdFor where p.id = :postId")
    Optional<Post> findWithCreatedFor(UUID postId);

    @Query("select p from Post p where p.createdFor.id = :actorId order by p.createdAt desc, p.id desc")
    Page<Post> findByCreatedFor(UUID actorId, Pageable pageable);

    @Query(value = "select id, kind from ("
            + "select id, created_at, 'post' as kind from post where created_for_id = :actorId "
            + "union all "
            + "select id, created_at, 'comment' as kind from comment where created_for_id = :actorId"
            + ") combined order by created_at desc, id desc limit :limit offset :offset", nativeQuery = true)
    List<Object[]> findActivityRowIds(UUID actorId, int limit, long offset);

    @Query(value = "select (select count(*) from post where created_for_id = :actorId)"
            + " + (select count(*) from comment where created_for_id = :actorId)", nativeQuery = true)
    long countActivityRows(UUID actorId);
}
