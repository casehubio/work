package io.casehub.work.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.queues.model.QueueSnapshot;
import io.casehub.work.queues.repository.QueueSnapshotStore;

@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryQueueSnapshotStore implements QueueSnapshotStore {

    private final List<QueueSnapshot> snapshots = new ArrayList<>();

    @Override
    public QueueSnapshot put(final QueueSnapshot snapshot) {
        if (snapshot.id == null) snapshot.id = UUID.randomUUID();
        snapshots.add(snapshot);
        return snapshot;
    }

    @Override
    public List<QueueSnapshot> findByQueueAndPeriod(
            final UUID queueViewId, final Instant from, final Instant to) {
        return snapshots.stream()
                .filter(s -> s.queueViewId.equals(queueViewId)
                        && !s.snapshotAt.isBefore(from)
                        && !s.snapshotAt.isAfter(to))
                .sorted((a, b) -> a.snapshotAt.compareTo(b.snapshotAt))
                .toList();
    }

    @Override
    public Map<UUID, Instant> findLatestSnapshotTimes(final Collection<UUID> queueViewIds) {
        final Map<UUID, Instant> result = new HashMap<>();
        for (final QueueSnapshot s : snapshots) {
            if (queueViewIds.contains(s.queueViewId)) {
                result.merge(s.queueViewId, s.snapshotAt,
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return result;
    }

    @Override
    public void deleteOlderThan(final Instant cutoff) {
        snapshots.removeIf(s -> s.snapshotAt.isBefore(cutoff));
    }

    public void clear() {
        snapshots.clear();
    }
}
