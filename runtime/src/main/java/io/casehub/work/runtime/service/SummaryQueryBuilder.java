package io.casehub.work.runtime.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;

public final class SummaryQueryBuilder {

    private SummaryQueryBuilder() {
    }

    public static WorkItemSummary build(final EntityManager em,
            final String baseJpql, final Map<String, Object> params,
            final boolean useDistinct, final Instant now) {

        final String countExpr = useDistinct ? "COUNT(DISTINCT wi)" : "COUNT(wi)";

        final Map<String, Long> byStatus = groupBy(em, baseJpql, params, countExpr, "wi.status", null);
        final long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        final Map<String, Long> byPriority = groupBy(em, baseJpql, params, countExpr,
                "wi.priority", "wi.priority IS NOT NULL");

        final long overdue = scalarCount(em, baseJpql, params, countExpr,
                "wi.status NOT IN (:_terminalStatuses) AND wi.expiresAt < :_now", now);

        final long claimDeadlineBreached = scalarCount(em, baseJpql, params, countExpr,
                "wi.status = :_pendingStatus AND wi.claimDeadline < :_now", now);

        final Instant oldestCreatedAt = scalarMin(em, baseJpql, params,
                "wi.status NOT IN (:_terminalStatuses)");

        return new WorkItemSummary(total, byStatus, byPriority, overdue,
                claimDeadlineBreached, oldestCreatedAt);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> groupBy(final EntityManager em,
            final String baseJpql, final Map<String, Object> params,
            final String countExpr, final String groupField, final String extraFilter) {

        final StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT ").append(groupField).append(", ").append(countExpr);
        jpql.append(" ").append(baseJpql);
        if (extraFilter != null) {
            jpql.append(" AND ").append(extraFilter);
        }
        jpql.append(" GROUP BY ").append(groupField);

        final Query query = em.createQuery(jpql.toString());
        params.forEach(query::setParameter);

        final List<Object[]> rows = query.getResultList();
        return rows.stream()
                .filter(r -> r[0] != null)
                .collect(Collectors.toMap(
                        r -> r[0].toString(),
                        r -> (Long) r[1]));
    }

    private static long scalarCount(final EntityManager em,
            final String baseJpql, final Map<String, Object> params,
            final String countExpr, final String extraFilter, final Instant now) {

        final String jpql = "SELECT " + countExpr + " " + baseJpql + " AND " + extraFilter;
        final Query query = em.createQuery(jpql);
        params.forEach(query::setParameter);
        query.setParameter("_now", now);
        if (extraFilter.contains("_terminalStatuses")) {
            query.setParameter("_terminalStatuses", WorkItemStatus.TERMINAL_STATUSES);
        }
        if (extraFilter.contains("_pendingStatus")) {
            query.setParameter("_pendingStatus", WorkItemStatus.PENDING);
        }
        return (Long) query.getSingleResult();
    }

    private static Instant scalarMin(final EntityManager em,
            final String baseJpql, final Map<String, Object> params,
            final String extraFilter) {

        final String jpql = "SELECT MIN(wi.createdAt) " + baseJpql + " AND " + extraFilter;
        final Query query = em.createQuery(jpql);
        params.forEach(query::setParameter);
        if (extraFilter.contains("_terminalStatuses")) {
            query.setParameter("_terminalStatuses", WorkItemStatus.TERMINAL_STATUSES);
        }
        return (Instant) query.getSingleResult();
    }
}
