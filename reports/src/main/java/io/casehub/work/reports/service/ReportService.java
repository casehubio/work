package io.casehub.work.reports.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

@ApplicationScoped
public class ReportService extends TenantAwareStore {


    @Transactional
    public SlaBreachReport slaBreaches(final Instant from, final Instant to,
            final String type, final WorkItemPriority priority) {
        return withTenantQuery(() -> slaBreachesInternal(from, to, type, priority));
    }

    private SlaBreachReport slaBreachesInternal(final Instant from, final Instant to,
            final String type, final WorkItemPriority priority) {
        final Instant now = Instant.now();

        final StringBuilder jpql = new StringBuilder(
                "SELECT w FROM WorkItem w WHERE w.tenancyId = :tenancyId")
                .append(" AND w.expiresAt IS NOT NULL AND (")
                .append("(w.status IN :activeStatuses AND w.expiresAt < :now)")
                .append(" OR ")
                .append("(w.status IN :terminalStatuses")
                .append(" AND w.completedAt IS NOT NULL AND w.completedAt > w.expiresAt))");

        if (from != null) {
            jpql.append(" AND w.expiresAt >= :from");
        }
        if (to != null) {
            jpql.append(" AND w.expiresAt <= :to");
        }
        if (type != null && !type.isBlank()) {
            jpql.append(" AND EXISTS (SELECT t FROM w.types t WHERE t.path = :type)");
        }
        if (priority != null) {
            jpql.append(" AND w.priority = :priority");
        }
        jpql.append(" ORDER BY w.expiresAt DESC");

        final TypedQuery<WorkItem> q = em.createQuery(jpql.toString(), WorkItem.class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("tenancyId", currentPrincipal.tenancyId());
        q.setParameter("now", now);
        q.setParameter("activeStatuses", java.util.Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isActive).toList());
        q.setParameter("terminalStatuses", java.util.Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isTerminal).toList());
        if (from != null) {
            q.setParameter("from", from);
        }
        if (to != null) {
            q.setParameter("to", to);
        }
        if (type != null && !type.isBlank()) {
            q.setParameter("type", type);
        }
        if (priority != null) {
            q.setParameter("priority", priority);
        }

        final List<WorkItem> breached = q.getResultList();

        final List<SlaBreachItem> items = new ArrayList<>();
        final Map<String, Long> byType = new LinkedHashMap<>();
        long totalMinutes = 0;

        for (final WorkItem wi : breached) {
            final Instant end = wi.completedAt != null ? wi.completedAt : now;
            final long mins = Math.max(0, ChronoUnit.MINUTES.between(wi.expiresAt, end));
            final String typesString = wi.types.stream()
                    .map(t -> t.path)
                    .collect(java.util.stream.Collectors.joining(", "));
            items.add(new SlaBreachItem(
                    wi.id.toString(),
                    typesString,
                    wi.priority != null ? wi.priority.name() : null,
                    wi.expiresAt,
                    wi.completedAt,
                    wi.status.name(),
                    mins));
            totalMinutes += mins;
            for (final io.casehub.work.runtime.model.WorkItemType wiType : wi.types) {
                byType.merge(wiType.path, 1L, Long::sum);
            }
        }

        final double avg = breached.isEmpty() ? 0.0 : (double) totalMinutes / breached.size();
        return new SlaBreachReport(items,
                new SlaSummary(breached.size(), Math.round(avg * 10.0) / 10.0, byType));
    }


    @Transactional
    public ActorReport actorPerformance(final String actorId, final Instant from,
            final Instant to, final String type) {
        return withTenantQuery(() -> actorPerformanceInternal(actorId, from, to, type));
    }

    private ActorReport actorPerformanceInternal(final String actorId, final Instant from,
            final Instant to, final String type) {
        final long totalAssigned = countAuditEvents(actorId, "ASSIGNED", from, to, type);
        final long totalCompleted = countAuditEvents(actorId, "COMPLETED", from, to, type);
        final long totalRejected = countAuditEvents(actorId, "REJECTED", from, to, type);

        // avg(completedAt - assignedAt) for items completed by this actor
        final StringBuilder avgJpql = new StringBuilder(
                "SELECT w.assignedAt, w.completedAt FROM WorkItem w"
                        + " WHERE w.tenancyId = :tenancyId"
                        + " AND w.assigneeId = :actorId"
                        + " AND w.completedAt IS NOT NULL AND w.assignedAt IS NOT NULL");
        if (from != null) {
            avgJpql.append(" AND w.completedAt >= :from");
        }
        if (to != null) {
            avgJpql.append(" AND w.completedAt <= :to");
        }
        if (type != null && !type.isBlank()) {
            avgJpql.append(" AND EXISTS (SELECT t FROM w.types t WHERE t.path = :type)");
        }

        final TypedQuery<Object[]> avgQ = em.createQuery(avgJpql.toString(), Object[].class);
        avgQ.setParameter("tenancyId", currentPrincipal.tenancyId());
        avgQ.setParameter("actorId", actorId);
        if (from != null) {
            avgQ.setParameter("from", from);
        }
        if (to != null) {
            avgQ.setParameter("to", to);
        }
        if (type != null && !type.isBlank()) {
            avgQ.setParameter("type", type);
        }

        final List<Object[]> rows = avgQ.getResultList();
        final Double avgCompletionMinutes = rows.isEmpty() ? null
                : rows.stream()
                        .mapToLong(r -> ChronoUnit.MINUTES.between((Instant) r[0], (Instant) r[1]))
                        .average()
                        .stream()
                        .boxed()
                        .map(d -> Math.round(d * 10.0) / 10.0)
                        .findFirst()
                        .orElse(null);

        // byType — join types collection, group by type path
        final StringBuilder typeJpql = new StringBuilder(
                "SELECT t.path, COUNT(w) FROM WorkItem w JOIN w.types t"
                        + " WHERE w.tenancyId = :tenancyId"
                        + " AND w.assigneeId = :actorId"
                        + " AND w.status = :completed");
        if (from != null) {
            typeJpql.append(" AND w.completedAt >= :from");
        }
        if (to != null) {
            typeJpql.append(" AND w.completedAt <= :to");
        }
        if (type != null && !type.isBlank()) {
            typeJpql.append(" AND t.path = :type");
        }
        typeJpql.append(" GROUP BY t.path");

        final TypedQuery<Object[]> typeQ = em.createQuery(typeJpql.toString(), Object[].class);
        typeQ.setParameter("tenancyId", currentPrincipal.tenancyId());
        typeQ.setParameter("actorId", actorId);
        typeQ.setParameter("completed", WorkItemStatus.COMPLETED);
        if (from != null) {
            typeQ.setParameter("from", from);
        }
        if (to != null) {
            typeQ.setParameter("to", to);
        }
        if (type != null && !type.isBlank()) {
            typeQ.setParameter("type", type);
        }

        final Map<String, Long> byType = new LinkedHashMap<>();
        for (final Object[] row : typeQ.getResultList()) {
            if (row[0] != null) {
                byType.put((String) row[0], (Long) row[1]);
            }
        }

        return new ActorReport(actorId, totalAssigned, totalCompleted, totalRejected,
                avgCompletionMinutes, byType);
    }


    @Transactional
    public ThroughputReport throughput(final Instant from, final Instant to, final String groupBy) {
        return withTenantQuery(() -> {
            final List<Object[]> dayCreated = queryDayBuckets("createdAt", from, to, null);
            final List<Object[]> dayCompleted = queryDayBucketsCompleted(from, to);
            final List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(dayCreated, dayCompleted, groupBy);
            return new ThroughputReport(from, to, groupBy, buckets);
        });
    }

    @Transactional
    public QueueHealthReport queueHealth(final String type, final WorkItemPriority priority) {
        return withTenantQuery(() -> queueHealthInternal(type, priority));
    }

    private QueueHealthReport queueHealthInternal(final String type, final WorkItemPriority priority) {
        final Instant now = Instant.now();
        final List<WorkItemStatus> activeStatuses = java.util.Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isActive).toList();

        // overdueCount
        final StringBuilder overdueJpql = new StringBuilder(
                "SELECT COUNT(w) FROM WorkItem w WHERE w.tenancyId = :tenancyId"
                        + " AND w.expiresAt IS NOT NULL"
                        + " AND w.expiresAt < :now AND w.status IN :activeStatuses");
        if (type != null && !type.isBlank()) {
            overdueJpql.append(" AND EXISTS (SELECT t FROM w.types t WHERE t.path = :type)");
        }
        if (priority != null) {
            overdueJpql.append(" AND w.priority = :priority");
        }

        final TypedQuery<Long> overdueQ = em.createQuery(overdueJpql.toString(), Long.class);
        overdueQ.setParameter("tenancyId", currentPrincipal.tenancyId());
        overdueQ.setParameter("now", now);
        overdueQ.setParameter("activeStatuses", activeStatuses);
        if (type != null && !type.isBlank()) {
            overdueQ.setParameter("type", type);
        }
        if (priority != null) {
            overdueQ.setParameter("priority", priority);
        }
        final long overdueCount = overdueQ.getSingleResult();

        // criticalOverdueCount
        final StringBuilder critJpql = new StringBuilder(
                "SELECT COUNT(w) FROM WorkItem w WHERE w.tenancyId = :tenancyId"
                        + " AND w.expiresAt IS NOT NULL"
                        + " AND w.expiresAt < :now AND w.status IN :activeStatuses"
                        + " AND w.priority = :critical");
        if (type != null && !type.isBlank()) {
            critJpql.append(" AND EXISTS (SELECT t FROM w.types t WHERE t.path = :type)");
        }

        final TypedQuery<Long> critQ = em.createQuery(critJpql.toString(), Long.class);
        critQ.setParameter("tenancyId", currentPrincipal.tenancyId());
        critQ.setParameter("now", now);
        critQ.setParameter("activeStatuses", activeStatuses);
        critQ.setParameter("critical", WorkItemPriority.URGENT);
        if (type != null && !type.isBlank()) {
            critQ.setParameter("type", type);
        }
        final long criticalOverdueCount = critQ.getSingleResult();

        // pending items: count, oldest, avg age
        final StringBuilder pendingJpql = new StringBuilder(
                "SELECT w.createdAt FROM WorkItem w WHERE w.tenancyId = :tenancyId"
                        + " AND w.status = :pending");
        if (type != null && !type.isBlank()) {
            pendingJpql.append(" AND EXISTS (SELECT t FROM w.types t WHERE t.path = :type)");
        }
        if (priority != null) {
            pendingJpql.append(" AND w.priority = :priority");
        }

        final TypedQuery<Instant> pendingQ = em.createQuery(pendingJpql.toString(), Instant.class);
        pendingQ.setParameter("tenancyId", currentPrincipal.tenancyId());
        pendingQ.setParameter("pending", WorkItemStatus.PENDING);
        if (type != null && !type.isBlank()) {
            pendingQ.setParameter("type", type);
        }
        if (priority != null) {
            pendingQ.setParameter("priority", priority);
        }

        final List<Instant> pendingCreatedAts = pendingQ.getResultList();
        final long pendingCount = pendingCreatedAts.size();
        final Instant oldestUnclaimed = pendingCreatedAts.stream().min(Instant::compareTo).orElse(null);
        final long avgPendingAgeSeconds = pendingCreatedAts.isEmpty() ? 0L
                : (long) pendingCreatedAts.stream()
                        .mapToLong(c -> ChronoUnit.SECONDS.between(c, now))
                        .average().orElse(0.0);

        return new QueueHealthReport(now, overdueCount, pendingCount,
                avgPendingAgeSeconds, oldestUnclaimed, criticalOverdueCount);
    }

    private long countAuditEvents(final String actorId, final String event,
            final Instant from, final Instant to, final String type) {
        final StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(a) FROM AuditEntry a WHERE a.tenancyId = :tenancyId"
                        + " AND a.actor = :actorId AND a.event = :event");
        if (from != null) {
            jpql.append(" AND a.occurredAt >= :from");
        }
        if (to != null) {
            jpql.append(" AND a.occurredAt <= :to");
        }
        if (type != null && !type.isBlank()) {
            jpql.append(" AND a.workItemId IN"
                    + " (SELECT w.id FROM WorkItem w JOIN w.types t WHERE w.tenancyId = :tenancyId AND t.path = :type)");
        }

        final TypedQuery<Long> q = em.createQuery(jpql.toString(), Long.class);
        q.setParameter("tenancyId", currentPrincipal.tenancyId());
        q.setParameter("actorId", actorId);
        q.setParameter("event", event);
        if (from != null) {
            q.setParameter("from", from);
        }
        if (to != null) {
            q.setParameter("to", to);
        }
        if (type != null && !type.isBlank()) {
            q.setParameter("type", type);
        }
        return q.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> queryDayBuckets(final String field, final Instant from,
            final Instant to, final WorkItemStatus status) {
        final String jpql = "SELECT CAST(date_trunc('day', w." + field + ") AS LocalDate), COUNT(w)"
                + " FROM WorkItem w"
                + " WHERE w.tenancyId = :tenancyId"
                + " AND w." + field + " >= :from AND w." + field + " <= :to"
                + (status != null ? " AND w.status = :status" : "")
                + " GROUP BY CAST(date_trunc('day', w." + field + ") AS LocalDate)"
                + " ORDER BY CAST(date_trunc('day', w." + field + ") AS LocalDate)";

        final TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("tenancyId", currentPrincipal.tenancyId());
        q.setParameter("from", from);
        q.setParameter("to", to);
        if (status != null) {
            q.setParameter("status", status);
        }
        return q.getResultList();
    }

    private List<Object[]> queryDayBucketsCompleted(final Instant from, final Instant to) {
        final String jpql = "SELECT CAST(date_trunc('day', w.completedAt) AS LocalDate), COUNT(w)"
                + " FROM WorkItem w"
                + " WHERE w.tenancyId = :tenancyId"
                + " AND w.completedAt >= :from AND w.completedAt <= :to"
                + " AND w.status IN :terminalStatuses"
                + " GROUP BY CAST(date_trunc('day', w.completedAt) AS LocalDate)"
                + " ORDER BY CAST(date_trunc('day', w.completedAt) AS LocalDate)";

        final TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("tenancyId", currentPrincipal.tenancyId());
        q.setParameter("from", from);
        q.setParameter("to", to);
        q.setParameter("terminalStatuses", java.util.Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isTerminal).toList());
        return q.getResultList();
    }
}
