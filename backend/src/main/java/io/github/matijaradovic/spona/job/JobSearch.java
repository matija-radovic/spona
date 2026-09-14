package io.github.matijaradovic.spona.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Component
class JobSearch {

    private final EntityManager entityManager;
    private final JobRepository jobs;

    JobSearch(EntityManager entityManager, JobRepository jobs) {
        this.entityManager = entityManager;
        this.jobs = jobs;
    }

    Page<Job> page(JobFilter filter, Pageable pageable) {
        Where where = where(filter);
        long total = ((Number) where.bind(entityManager.createNativeQuery("select count(*) from job j" + where.sql()))
                .getSingleResult()).longValue();
        if (pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        List<?> rows = where.bind(entityManager.createNativeQuery(
                "select j.id from job j" + where.sql() + orderBy(filter.sort()) + " limit :limit offset :offset"))
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();
        List<UUID> ids = rows.stream().map(id -> (UUID) id).toList();
        return new PageImpl<>(jobs.findAllInOrder(ids), pageable, total);
    }

    private static Where where(JobFilter filter) {
        StringBuilder sql = new StringBuilder(" where j.status = 'OPEN'");
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (filter.q() != null) {
            sql.append(" and j.search_vector @@ websearch_to_tsquery('sr', :q)");
            parameters.put("q", filter.q());
        }
        if (filter.category() != null) {
            sql.append(" and j.category = cast(:category as job_category)");
            parameters.put("category", filter.category().name());
        }
        if (filter.posted() != null) {
            sql.append(" and j.created_at >= now() - cast(:posted as interval)");
            parameters.put("posted", filter.posted().window());
        }
        anyOf(sql, parameters, "j.workplace_type", "workplace", filter.workplace());
        anyOf(sql, parameters, "j.job_type", "employment", filter.employment());
        anyOf(sql, parameters, "j.working_hours", "hours", filter.hours());
        anyOf(sql, parameters, "j.experience_level", "experience", filter.experience());
        if (filter.salary()) {
            sql.append(" and exists (select 1 from salary s where s.job_id = j.id)");
        }
        return new Where(sql.toString(), parameters);
    }

    /** A plain IN, so a NULL column matches no list. */
    private static void anyOf(StringBuilder sql, Map<String, Object> parameters, String column, String name,
            Set<? extends Enum<?>> values) {
        if (!values.isEmpty()) {
            sql.append(" and cast(").append(column).append(" as text) in (:").append(name).append(')');
            parameters.put(name, values.stream().map(value -> value.name()).toList());
        }
    }

    /** Every order ends on the id, so tied rows page without repeats or gaps. */
    private static String orderBy(JobFilter.Sort sort) {
        return switch (sort) {
            case RELEVANCE -> " order by ts_rank_cd(j.search_vector, websearch_to_tsquery('sr', :q)) desc,"
                    + " j.created_at desc, j.id desc";
            case DATE_DESC -> " order by j.created_at desc, j.id desc";
            case DATE_ASC -> " order by j.created_at, j.id";
        };
    }

    private record Where(String sql, Map<String, Object> parameters) {

        Query bind(Query query) {
            parameters.forEach((name, value) -> query.setParameter(name, value));
            return query;
        }
    }
}
