package io.github.matijaradovic.spona.job.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    long countByJob_Id(UUID jobId);

    @Query(value = "select a from JobApplication a join fetch a.createdBy"
            + " where a.job.id = :jobId order by a.submittedAt desc, a.id desc",
            countQuery = "select count(a) from JobApplication a where a.job.id = :jobId")
    Page<JobApplication> findInbox(UUID jobId, Pageable pageable);
}
