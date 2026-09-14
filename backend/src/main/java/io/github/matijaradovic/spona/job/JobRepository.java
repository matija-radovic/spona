package io.github.matijaradovic.spona.job;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query("select j from Job j join fetch j.createdFor left join fetch j.salary where j.slug = :slug")
    Optional<Job> findBySlugWithCreatedFor(String slug);

    @Query("select j from Job j join fetch j.createdFor left join fetch j.salary where j.id in :ids")
    List<Job> findAllWithCreatedFor(Collection<UUID> ids);

    @Query("select j from Job j join fetch j.createdFor left join fetch j.salary"
            + " where j.id in :ids and j.status in :statuses")
    List<Job> findAllWithCreatedFor(Collection<UUID> ids, Collection<JobStatus> statuses);

    default List<Job> findAllInOrder(List<UUID> ids) {
        return inOrder(ids, ids.isEmpty() ? List.of() : findAllWithCreatedFor(ids));
    }

    default List<Job> findAllInOrder(List<UUID> ids, Collection<JobStatus> statuses) {
        return inOrder(ids, ids.isEmpty() || statuses.isEmpty() ? List.of() : findAllWithCreatedFor(ids, statuses));
    }

    private static List<Job> inOrder(List<UUID> ids, List<Job> rows) {
        Map<UUID, Job> byId = rows.stream().collect(Collectors.toMap(job -> job.getId(), job -> job));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
