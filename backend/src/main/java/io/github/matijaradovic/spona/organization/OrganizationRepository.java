package io.github.matijaradovic.spona.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select o from Organization o"
            + " where locate(lower(:q), lower(o.displayName)) > 0 or locate(lower(:q), lower(o.slug)) > 0"
            + " order by o.slug")
    Page<Organization> search(String q, Pageable pageable);
}
