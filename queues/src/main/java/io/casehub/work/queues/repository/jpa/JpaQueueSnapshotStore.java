package io.casehub.work.queues.repository.jpa;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.queues.model.QueueSnapshot;
import io.casehub.work.queues.repository.QueueSnapshotStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

@ApplicationScoped
public class JpaQueueSnapshotStore extends TenantAwareStore implements QueueSnapshotStore {

    @Override
    public QueueSnapshot put(final QueueSnapshot snapshot) {
        return withTenantQuery(() -> {
            if (snapshot.tenancyId == null) {
                snapshot.tenancyId = currentPrincipal.tenancyId();
            }
            snapshot.persistAndFlush();
            return snapshot;
        });
    }

    @Override
    public List<QueueSnapshot> findByQueueAndPeriod(
            final UUID queueViewId, final Instant from, final Instant to) {
        return withTenantQuery(() ->
                QueueSnapshot.<QueueSnapshot>find(
                        "tenancyId = ?1 AND queueViewId = ?2 AND snapshotAt >= ?3 AND snapshotAt <= ?4 ORDER BY snapshotAt ASC",
                        currentPrincipal.tenancyId(), queueViewId, from, to)
                        .list());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, Instant> findLatestSnapshotTimes(final Collection<UUID> queueViewIds) {
        if (queueViewIds.isEmpty()) return Map.of();
        return withTenantQuery(() -> {
            final var em = QueueSnapshot.getEntityManager();
            final List<Object[]> rows = em.createQuery(
                            "SELECT qs.queueViewId, MAX(qs.snapshotAt) FROM QueueSnapshot qs " +
                                    "WHERE qs.tenancyId = :tenancyId AND qs.queueViewId IN :ids GROUP BY qs.queueViewId")
                    .setParameter("tenancyId", currentPrincipal.tenancyId())
                    .setParameter("ids", queueViewIds)
                    .getResultList();
            final Map<UUID, Instant> result = new HashMap<>();
            for (final Object[] row : rows) {
                result.put((UUID) row[0], (Instant) row[1]);
            }
            return result;
        });
    }

    @Override
    public void deleteOlderThan(final Instant cutoff) {
        withTenantQuery(() -> {
            QueueSnapshot.delete("tenancyId = ?1 AND snapshotAt < ?2",
                    currentPrincipal.tenancyId(), cutoff);
            return null;
        });
    }
}
